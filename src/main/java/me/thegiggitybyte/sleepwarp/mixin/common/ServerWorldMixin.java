package me.thegiggitybyte.sleepwarp.mixin.common;

import me.thegiggitybyte.sleepwarp.SleepWarp;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.SleepManager;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.GameRules;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.level.ServerWorldProperties;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin extends World {
    @Unique private static final long DAY_LENGTH = 24000;
    
    @Shadow @Final private ServerWorldProperties worldProperties;
    @Shadow @Final List<ServerPlayerEntity> players;
    
    protected ServerWorldMixin(MutableWorldProperties properties, RegistryKey<World> registryRef, RegistryEntry<DimensionType> registryEntry, Supplier<Profiler> profiler, boolean isClient, boolean debugWorld, long seed, int maxChainedNeighborUpdates) {
        super(properties, registryRef, registryEntry, profiler, isClient, debugWorld, seed, maxChainedNeighborUpdates);
    }
    
    @Shadow @NotNull public abstract MinecraftServer getServer();
    @Shadow protected abstract void resetWeather();
    @Shadow protected abstract void wakeSleepingPlayers();
    
    @Inject(method = "tick", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/world/GameRules;getInt(Lnet/minecraft/world/GameRules$Key;)I"))
    private void trySleepWarp(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        // Pre-warp checks.
        var sleepingCount = this.players.stream().filter(PlayerEntity::canResetTimeBySleeping).count();
        if (sleepingCount == 0) return;
    
        if (canWarpTime() == false) return;
        
        // Calculate amount of ticks to add to time.
        var maximumTicks = Math.max(1, SleepWarp.CONFIGURATION.get("max_ticks_added").getAsInt());
        var worldTime = (this.worldProperties.getTimeOfDay() % DAY_LENGTH);
        long ticksAdded;
    
        if (worldTime + maximumTicks < DAY_LENGTH) {
            var playerScale = BigDecimal.valueOf(Math.max(0.05, Math.min(1.0, SleepWarp.CONFIGURATION.get("player_scale").getAsDouble())));
            var sleepingRatio = BigDecimal.valueOf(sleepingCount).divide(BigDecimal.valueOf(this.players.size()), 5, RoundingMode.HALF_UP);
            var scaledRatio = sleepingRatio.multiply(playerScale);
            var tickScale = scaledRatio.divide(scaledRatio.multiply(BigDecimal.valueOf(2)).subtract(playerScale).subtract(sleepingRatio).add(BigDecimal.ONE), RoundingMode.HALF_UP);
    
            ticksAdded = BigDecimal.valueOf(maximumTicks).multiply(tickScale)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
        } else {
            ticksAdded = (DAY_LENGTH % worldTime);
        }
    
        // Remove some ticks if the server is overloaded.
        var performanceMode = SleepWarp.CONFIGURATION.get("performance_mode").getAsBoolean();
        var tpsLoss = SleepWarp.TICK_MONITOR.getAverageTickLoss();
        
        if (tpsLoss > 0 & performanceMode) {
            var pendingTicks = BigDecimal.valueOf(ticksAdded).setScale(5, RoundingMode.HALF_UP);
            var tickRate = SleepWarp.TICK_MONITOR.getAverageTickRate();
    
            var ticksRemoved = pendingTicks.divide(BigDecimal.valueOf(tpsLoss), RoundingMode.HALF_UP);
            var pendingSeconds = pendingTicks.divide(tickRate, RoundingMode.HALF_UP);
    
            ticksRemoved = (pendingSeconds.compareTo(BigDecimal.ONE) < 0)
                    ? ticksRemoved.multiply(pendingSeconds).setScale(0, RoundingMode.HALF_UP)
                    : ticksRemoved.divide(pendingSeconds, 0, RoundingMode.HALF_UP);
    
            ticksAdded = pendingTicks.subtract(ticksRemoved).longValue();
        }
        
        // Set time and update clients.
        this.worldProperties.setTimeOfDay(this.worldProperties.getTimeOfDay() + ticksAdded);
        this.worldProperties.setTime(this.properties.getTime() + ticksAdded);
    
        worldTime += ticksAdded;
        
        var doDaylightCycle = this.worldProperties.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
        var packet = new WorldTimeUpdateS2CPacket(this.getTime(), this.getTimeOfDay(), doDaylightCycle);
        this.getServer().getPlayerManager().sendToDimension(packet, this.getRegistryKey());
        
        // Simulate world if desired by user and the server is not under load.
        if (SleepWarp.CONFIGURATION.get("tick_chunks").getAsBoolean() && (!performanceMode || tpsLoss <= 3))
            for (int tick = 0; tick < ticksAdded; tick++)
                this.getChunkManager().tick(() -> true, true);
        
        if (SleepWarp.CONFIGURATION.get("tick_block_entities").getAsBoolean() && (!performanceMode || tpsLoss <= 5))
            for (int tick = 0; tick < ticksAdded; tick++)
                this.tickBlockEntities();
        
        // Display sleep status message.
        if (SleepWarp.CONFIGURATION.get("action_bar_message").getAsBoolean()) {
            var actionBarText = Text.empty().formatted(Formatting.WHITE);
            var remainingTicks = DAY_LENGTH - worldTime;
    
            if (remainingTicks > 0) {
                var remainingSeconds = BigDecimal.valueOf(remainingTicks)
                        .setScale(1, RoundingMode.HALF_UP)
                        .divide(BigDecimal.valueOf(ticksAdded), RoundingMode.HALF_UP)
                        .divide(SleepWarp.TICK_MONITOR.getAverageTickRate(), RoundingMode.HALF_DOWN);
                
                Formatting hourglassColor;
                if (performanceMode) {
                    if (tpsLoss <= 1)
                        hourglassColor = Formatting.DARK_GREEN;
                    else if (tpsLoss <= 3)
                        hourglassColor = Formatting.YELLOW;
                    else if (tpsLoss <= 5)
                        hourglassColor = Formatting.RED;
                    else
                        hourglassColor = Formatting.DARK_RED;
                } else {
                    hourglassColor = Formatting.GOLD;
                }
                
                actionBarText.append(remainingSeconds.toPlainString())
                        .append(" seconds until sunrise ")
                        .append(Text.literal("⌛").formatted(hourglassColor));
            } else {
                var currentDay = BigDecimal.valueOf(this.getTime()).divide(BigDecimal.valueOf(DAY_LENGTH), RoundingMode.HALF_UP);
                actionBarText.append("Day ").append(Text.literal(currentDay.toPlainString()).formatted(Formatting.GOLD));
        
                if (this.isRaining() && this.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE)) {
                    this.resetWeather();
                }
        
                this.wakeSleepingPlayers();
            }
    
            this.players.forEach(player -> player.sendMessage(actionBarText, true));
        }
    }
    
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/SleepManager;canSkipNight(I)Z"))
    private boolean suppressVanillaSleep(SleepManager instance, int percentage) {
        return false;
    }
    
    @Redirect(method = "updateSleepingPlayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;sendSleepingStatus()V"))
    private void suppressSleepNotifications(ServerWorld instance) {
    }
    
    private boolean canWarpTime() {
        var sleepingPlayerCount = BigDecimal.valueOf(this.players.stream().filter(PlayerEntity::canResetTimeBySleeping).count());
        if (sleepingPlayerCount.equals(BigDecimal.ZERO))
            return false;
    
        if (SleepWarp.CONFIGURATION.get("use_sleep_percentage").getAsBoolean()) {
            var percentage = BigDecimal.valueOf(this.getGameRules().getInt(GameRules.PLAYERS_SLEEPING_PERCENTAGE));
            var sleepingPlayerRequirement = sleepingPlayerCount.multiply(percentage).divide(BigDecimal.valueOf(100), RoundingMode.CEILING);
            return sleepingPlayerCount.compareTo(sleepingPlayerRequirement) >= 0;
        } else {
            return true;
        }
    }
}
