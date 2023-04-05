package me.thegiggitybyte.sleepwarp.mixin.common;

import me.thegiggitybyte.sleepwarp.SleepWarp;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.SleepManager;
import net.minecraft.text.Text;
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
    @Unique private static final BigDecimal DAY_LENGTH = BigDecimal.valueOf(24000);
    @Shadow @Final private ServerWorldProperties worldProperties;
    @Shadow @Final List<ServerPlayerEntity> players;
    
    protected ServerWorldMixin(MutableWorldProperties properties, RegistryKey<World> registryRef, DynamicRegistryManager registryManager, RegistryEntry<DimensionType> dimensionEntry, Supplier<Profiler> profiler, boolean isClient, boolean debugWorld, long biomeAccess, int maxChainedNeighborUpdates) {
        super(properties, registryRef, registryManager, dimensionEntry, profiler, isClient, debugWorld, biomeAccess, maxChainedNeighborUpdates);
    }
    
    @Shadow @NotNull public abstract MinecraftServer getServer();
    @Shadow protected abstract void tickWeather();
    @Shadow protected abstract void resetWeather();
    @Shadow protected abstract void wakeSleepingPlayers();
    
    @Inject(method = "tick", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/world/GameRules;getInt(Lnet/minecraft/world/GameRules$Key;)I"))
    private void trySleepWarp(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        // Pre-warp checks.
        var totalPlayers = this.players.size();
        var sleepingPlayers = this.players.stream().filter(player -> player.canResetTimeBySleeping()).count();
        if (sleepingPlayers == 0) return;
        
        if (SleepWarp.USER_CONFIGURATION.get("use_sleep_percentage").getAsBoolean()) {
            var percentRequired = BigDecimal.valueOf(this.getGameRules().getInt(GameRules.PLAYERS_SLEEPING_PERCENTAGE));
            var minimumSleeping = BigDecimal.ONE.max(BigDecimal.valueOf(totalPlayers).multiply(percentRequired).divide(BigDecimal.valueOf(100), RoundingMode.CEILING)).intValue();
            if (sleepingPlayers < minimumSleeping) return;
        }
        
        // Calculate amount of ticks to add to time.
        var maxTicksAdded = Math.max(1, SleepWarp.USER_CONFIGURATION.get("max_ticks_added").getAsInt());
        var playerScale = BigDecimal.valueOf(Math.max(0.05, Math.min(1.0, SleepWarp.USER_CONFIGURATION.get("player_scale").getAsDouble())));
        var timeOfDay = BigDecimal.valueOf(this.worldProperties.getTimeOfDay()).remainder(DAY_LENGTH);
        long ticksAdded;
    
        if (timeOfDay.longValue() + maxTicksAdded < DAY_LENGTH.longValue()) {
            var sleepingRatio = BigDecimal.valueOf(sleepingPlayers).divide(BigDecimal.valueOf(totalPlayers), 5, RoundingMode.HALF_UP);
            var scaledRatio = sleepingRatio.multiply(playerScale);
            var tickScale = scaledRatio.divide(scaledRatio.multiply(BigDecimal.valueOf(2)).subtract(playerScale).subtract(sleepingRatio).add(BigDecimal.ONE), RoundingMode.HALF_UP);
    
            ticksAdded = BigDecimal.valueOf(maxTicksAdded).multiply(tickScale).setScale(0, RoundingMode.HALF_UP).longValue();
        } else {
            ticksAdded = DAY_LENGTH.remainder(timeOfDay).longValue();
        }
    
        // Remove some ticks if the server is overloaded.
        var performanceMode = SleepWarp.USER_CONFIGURATION.get("performance_mode").getAsBoolean();
        var averageTicksSkipped = SleepWarp.TICK_MONITOR.getAverageTicksSkipped();
        var pendingTicks = BigDecimal.valueOf(ticksAdded);
        
        if (averageTicksSkipped.intValue() > 0 & performanceMode) {
            
            var averageTickRate = SleepWarp.TICK_MONITOR.getAverageTickRate();
            var tickPercentageSkipped = BigDecimal.valueOf(Math.pow(averageTicksSkipped.doubleValue(), 1.17)).divide(averageTickRate, RoundingMode.HALF_UP);
            var ticksRemoved = pendingTicks.multiply(tickPercentageSkipped);
    
            pendingTicks = pendingTicks.subtract(ticksRemoved).setScale(0, RoundingMode.HALF_UP);
            ticksAdded = pendingTicks.longValue();
        }
        
        timeOfDay = timeOfDay.add(pendingTicks);
        
        // Set time, process events, update clients.
        var worldTime = this.worldProperties.getTime() + ticksAdded;
        this.worldProperties.setTime(worldTime);
        this.worldProperties.setTimeOfDay(this.worldProperties.getTimeOfDay() + ticksAdded);
        this.worldProperties.getScheduledEvents().processEvents(this.getServer(), worldTime);
        
        var doDaylightCycle = this.worldProperties.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
        var packet = new WorldTimeUpdateS2CPacket(this.getTime(), this.getTimeOfDay(), doDaylightCycle);
        this.getServer().getPlayerManager().sendToDimension(packet, this.getRegistryKey());
        
        // Simulate world.
        for (int tick = 0; tick < ticksAdded; tick++) {
            if (this.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE))
                this.tickWeather();
            
            if (SleepWarp.USER_CONFIGURATION.get("tick_chunks").getAsBoolean() && !performanceMode || averageTicksSkipped.intValue() < 2)
                this.getChunkManager().tick(() -> true, true);
            
            if (SleepWarp.USER_CONFIGURATION.get("tick_block_entities").getAsBoolean() && !performanceMode || averageTicksSkipped.intValue() < 4)
                this.tickBlockEntities();
            
            // TODO: tick entities
            
            // TODO: tick liquids
        }
        
        // Display sleep status message.
        if (SleepWarp.USER_CONFIGURATION.get("action_bar_messages").getAsBoolean()) {
            var actionBarText = Text.empty().formatted(Formatting.WHITE);
            
            // Calculate remaining time
            long remainingTicks;
            
            if (this.isThundering()) {
                remainingTicks = this.worldProperties.getThunderTime();
            } else {
                remainingTicks = DAY_LENGTH.subtract(timeOfDay).longValue();
                
                // Wake players if needed.
                if (remainingTicks == 0) {
                    if (this.isRaining()) this.resetWeather();
                    this.wakeSleepingPlayers();
                    
                    var currentDay = BigDecimal.valueOf(this.getTime()).divide(DAY_LENGTH, RoundingMode.HALF_UP);
                    actionBarText.append("Day ").append(Text.literal(currentDay.toPlainString()).formatted(Formatting.GOLD));
                }
            }
    
            if (remainingTicks > 20) {
                var requiredPercentage = BigDecimal.ONE.setScale(1, RoundingMode.UNNECESSARY).subtract(playerScale.setScale(1, RoundingMode.HALF_EVEN));
                var actualPercentage = BigDecimal.valueOf(sleepingPlayers).divide(BigDecimal.valueOf(totalPlayers), 1, RoundingMode.HALF_EVEN);
                var difference = actualPercentage.compareTo(requiredPercentage);
                var hourglassEmojiColor = (difference >= 0) ? Formatting.DARK_GREEN : Formatting.RED;
                
                actionBarText.append(Text.literal("⌛ ").formatted(hourglassEmojiColor));
                
                var remainingSeconds = BigDecimal.valueOf(remainingTicks)
                        .setScale(5, RoundingMode.UNNECESSARY)
                        .divide(BigDecimal.valueOf(ticksAdded), RoundingMode.HALF_EVEN)
                        .divide(SleepWarp.TICK_MONITOR.getAverageTickRate(), RoundingMode.HALF_EVEN)
                        .setScale(0, RoundingMode.HALF_EVEN)
                        .toPlainString();
                
                actionBarText.append(Text.literal(remainingSeconds))
                        .append(" seconds until ")
                        .append(this.isThundering() ? "the thunderstorm passes" : "dawn");
            }
    
            this.players.forEach(player -> player.sendMessage(actionBarText, true));
        }
    }
    
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/SleepManager;canSkipNight(I)Z"))
    private boolean suppressVanillaSleep(SleepManager instance, int percentage) {
        return false;
    }
    
    @Redirect(method = "updateSleepingPlayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;sendSleepingStatus()V"))
    private void sendUpdatedSleepStatus(ServerWorld instance) {
        if (SleepWarp.USER_CONFIGURATION.get("action_bar_messages").getAsBoolean() == false) return;
        if (SleepWarp.USER_CONFIGURATION.get("use_sleep_percentage").getAsBoolean() == false) return;
        
        long playerCount = 0, inBedCount = 0, sleepingCount = 0;
        
        for (var player : players) {
            if (player.isSleeping()) {
                if (player.getSleepTimer() >= 100) ++sleepingCount;
                ++inBedCount;
            }
            
            ++playerCount;
        }
        
        var percentRequired = BigDecimal.valueOf(this.getGameRules().getInt(GameRules.PLAYERS_SLEEPING_PERCENTAGE));
        var minSleepingCount = BigDecimal.ONE.max(BigDecimal.valueOf(playerCount).multiply(percentRequired).divide(BigDecimal.valueOf(100), RoundingMode.CEILING)).intValue();
        var messageText = Text.empty();
        var tallyText = Text.empty()
                .append(Text.literal(String.valueOf(inBedCount)))
                .append("/")
                .append(Text.literal(String.valueOf(playerCount)));
        
        if (inBedCount == 0) {
            if (this.thunderGradient == 0.0)
                messageText.append(tallyText.formatted(Formatting.GRAY)).append(" players sleeping");
            else
                messageText.append(" ");
        } else if (sleepingCount < minSleepingCount && minSleepingCount - inBedCount > 0) {
            messageText.append(tallyText.formatted(Formatting.RED))
                    .append(" players sleeping. ")
                    .append(String.valueOf((minSleepingCount - inBedCount)))
                    .append(" more needed to advance time.");
        } else
            messageText.append(tallyText.formatted(Formatting.DARK_GREEN)).append(" players sleeping");
        
        this.players.forEach(player -> player.sendMessage(messageText, true));
    }
}
