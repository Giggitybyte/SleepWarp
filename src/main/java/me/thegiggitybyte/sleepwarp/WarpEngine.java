package me.thegiggitybyte.sleepwarp;

import me.thegiggitybyte.sleepwarp.config.JsonConfiguration;
import me.thegiggitybyte.sleepwarp.runnable.*;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Handles incrementing time and simulating the world.
 */
public class WarpEngine {
    public static final int DAY_LENGTH_TICKS = 24000;
    private static WarpEngine instance;
    private Random random;
    
    private WarpEngine() {
        random = new Random();
        
        EntitySleepEvents.ALLOW_SLEEP_TIME.register(this::allowSleepTime);
        ServerTickEvents.END_WORLD_TICK.register(this::onEndTick);
    }
    
    public static void initialize() {
        if (instance != null) throw new AssertionError();
        instance = new WarpEngine();
    }
    
    private ActionResult allowSleepTime(PlayerEntity player, BlockPos sleepingPos, boolean vanillaResult) {
        if (!vanillaResult && (player.getWorld().getTimeOfDay() % DAY_LENGTH_TICKS > 12542))
            return ActionResult.SUCCESS;
        else
            return ActionResult.PASS;
    }
    
    private void onEndTick(ServerWorld world) {
        // Pre-warp checks.
        if (!world.isSleepingEnabled()) return;
        
        var totalPlayers = world.getPlayers().size();
        var sleepingPlayers = world.getPlayers().stream().filter(PlayerEntity::canResetTimeBySleeping).count();
        if (sleepingPlayers == 0) return;
        
        if (JsonConfiguration.getUserInstance().getValue("use_sleep_percentage").getAsBoolean()) {
            var percentRequired = world.getGameRules().getInt(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
            var minimumSleeping = Math.max(1, (totalPlayers * percentRequired) / 100);
            if (sleepingPlayers < minimumSleeping) return;
        }
        
        // Determine amount of ticks to add to time.
        var maxTicksAdded = Math.max(10, JsonConfiguration.getUserInstance().getValue("max_ticks_added").getAsInt());
        var playerMultiplier = Math.max(0.05, Math.min(1.0, JsonConfiguration.getUserInstance().getValue("player_multiplier").getAsDouble()));
        var worldTime = world.getTimeOfDay() % DAY_LENGTH_TICKS;
        int warpTickCount;
        
        if (worldTime + maxTicksAdded < DAY_LENGTH_TICKS) {
            if (totalPlayers == 1) {
                warpTickCount = maxTicksAdded;
            } else {
                var sleepingRatio = (double) sleepingPlayers / totalPlayers;
                var scaledRatio = sleepingRatio * playerMultiplier;
                var tickMultiplier = scaledRatio / ((scaledRatio * 2) - playerMultiplier - sleepingRatio + 1);
                
                warpTickCount = Math.toIntExact(Math.round(maxTicksAdded * tickMultiplier));
            }
        } else {
            warpTickCount = Math.toIntExact(DAY_LENGTH_TICKS % worldTime);
        }
        
        // Collect valid chunks to tick.
        var chunkStorage = world.getChunkManager().threadedAnvilChunkStorage;
        var chunks = new ArrayList<WorldChunk>();
        
        for (ChunkHolder chunkHolder : chunkStorage.entryIterator()) {
            WorldChunk chunk = chunkHolder.getWorldChunk();
            
            if (chunk != null && world.shouldTick(chunk.getPos()) && chunkStorage.shouldTick(chunk.getPos())) {
                chunks.add(chunk);
            }
        }
        
        // Accelerate time and tick world.
        var doDaylightCycle = world.worldProperties.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
        
        for (var tick = 0; tick < warpTickCount; tick++) {
            world.tickWeather();
            world.calculateAmbientDarkness();
            world.tickTime();
            
            var packet = new WorldTimeUpdateS2CPacket(world.getTime(), world.getTimeOfDay(), doDaylightCycle);
            world.getServer().getPlayerManager().sendToDimension(packet, world.getRegistryKey());
            
            Collections.shuffle(chunks);
            for (var chunk : chunks) {
                if (JsonConfiguration.getUserInstance().getValue("tick_random_block").getAsBoolean()) {
                    CompletableFuture.runAsync(new RandomTickRunnable(world, chunk));
                }
                
                if (world.isRaining()) {
                    if (JsonConfiguration.getUserInstance().getValue("tick_lightning").getAsBoolean() && world.isThundering() && random.nextInt(100000) == 0) {
                        CompletableFuture.runAsync(new LightningTickRunnable(world, chunk));
                    }
                    
                    if (random.nextInt(16) == 0) {
                        CompletableFuture.runAsync(new PrecipitationTickRunnable(world, chunk));
                    }
                }
            }
            
            if (JsonConfiguration.getUserInstance().getValue("tick_block_entities").getAsBoolean()) {
                CompletableFuture.runAsync(new BlockTickRunnable(world));
            }
        }
        
        var canTickAnimals = JsonConfiguration.getUserInstance().getValue("tick_animals").getAsBoolean();
        var canTickMonsters = JsonConfiguration.getUserInstance().getValue("tick_monsters").getAsBoolean();
        if (canTickAnimals | canTickMonsters) {
            CompletableFuture.runAsync(new MobTickRunnable(world, warpTickCount));
        }
        
        worldTime = world.getTimeOfDay() % DAY_LENGTH_TICKS;
        var actionBarText = Text.empty().formatted(Formatting.WHITE);
        
        if (worldTime == 0) {
            if (world.isRaining()) world.resetWeather();
            world.wakeSleepingPlayers();
            
            var currentDay = String.valueOf(world.getTimeOfDay() / DAY_LENGTH_TICKS);
            actionBarText.append("Day ").append(Text.literal(currentDay).formatted(Formatting.GOLD));
        } else if (worldTime > 0) {
            var remainingTicks = world.isThundering()
                    ? world.worldProperties.getThunderTime()
                    : DAY_LENGTH_TICKS - worldTime;
            
            if (remainingTicks > 0) {
                if (totalPlayers > 1) {
                    var requiredPercentage = 1.0 - playerMultiplier;
                    var actualPercentage = (double) sleepingPlayers / totalPlayers;
                    var indicatorColor = actualPercentage >= requiredPercentage ? Formatting.DARK_GREEN : Formatting.RED;
                    var playerNoun = (sleepingPlayers == 1 ? "player" : "players");
                    
                    actionBarText.append(Text.literal("⌛ " + sleepingPlayers + ' ').formatted(indicatorColor));
                    actionBarText.append(Text.literal(playerNoun + " sleeping. "));
                } else {
                    actionBarText.append(Text.literal("⌛ ").formatted(Formatting.GOLD));
                }
                
                var remainingSeconds = Math.round(((double) remainingTicks / warpTickCount) / 20);
                actionBarText.append(Text.literal(String.valueOf(remainingSeconds)));
                actionBarText.append("s until ")
                        .append(world.isThundering() ? "the thunderstorm passes" : "dawn");
            }
        }
        
        if (JsonConfiguration.getUserInstance().getValue("action_bar_messages").getAsBoolean()) {
            world.getPlayers().forEach(player -> player.sendMessage(actionBarText, true));
        }
    }
}