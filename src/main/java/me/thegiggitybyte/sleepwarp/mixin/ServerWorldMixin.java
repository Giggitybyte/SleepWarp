package me.thegiggitybyte.sleepwarp.mixin;

import me.thegiggitybyte.sleepwarp.SleepWarp;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.SleepManager;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.GameRules;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.level.ServerWorldProperties;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin extends World {
    @Shadow @Final private ServerWorldProperties worldProperties;
    @Shadow @Final private SleepManager sleepManager;
    
    protected ServerWorldMixin(MutableWorldProperties properties, RegistryKey<World> registryRef, RegistryEntry<DimensionType> registryEntry, Supplier<Profiler> profiler, boolean isClient, boolean debugWorld, long seed, int maxChainedNeighborUpdates) {
        super(properties, registryRef, registryEntry, profiler, isClient, debugWorld, seed, maxChainedNeighborUpdates);
    }
    
    
    @Shadow @NotNull public abstract MinecraftServer getServer();
    @Shadow protected abstract void wakeSleepingPlayers();
    @Shadow protected abstract void resetWeather();
    
    @Inject(method = "tick", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/world/GameRules;getInt(Lnet/minecraft/world/GameRules$Key;)I"))
    private void trySleepWarp(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        // Pre-warp checks.
        var sleepTracker = (SleepManagerAccessor) this.sleepManager;
        if (sleepTracker.getSleeping() == 0) return;
        
        var sleepingCount = ((ServerWorldAccessor) this).getPlayers().stream()
                .filter(PlayerEntity::canResetTimeBySleeping)
                .count();
        if (sleepingCount == 0) return;
        
        boolean shouldUseSleepPercentage = SleepWarp.getConfig().getOrDefault("useSleepPercentage", false);
        if (shouldUseSleepPercentage) {
            var percentage = this.getGameRules().getInt(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
            boolean canWarpTime = sleepingCount >= this.sleepManager.getNightSkippingRequirement(percentage);
            if (canWarpTime == false) return;
        }
        
        // Calculate amount of ticks to add to time.
        var accelerationCurve = Math.max(0.1, Math.min(1.0, SleepWarp.getConfig().getOrDefault("accelerationCurve", 0.2)));
        var maxTicksAdded = Math.max(1, SleepWarp.getConfig().getOrDefault("maxTimeAdded", 60));
        long ticksAdded = 0;
        
        if (sleepTracker.getTotal() - sleepingCount > 0) {
            var sleepingRatio = (double) sleepingCount / (double) sleepTracker.getTotal();
            ticksAdded = (long) (maxTicksAdded * (accelerationCurve * sleepingRatio / (2.0 * accelerationCurve * sleepingRatio - accelerationCurve - sleepingRatio + 1.0)));
        } else {
            ticksAdded = maxTicksAdded;
        }
        
        // Set time and update clients.
        this.worldProperties.setTimeOfDay(this.worldProperties.getTimeOfDay() + ticksAdded);
        
        var doDaylightCycle = this.worldProperties.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
        var packet = new WorldTimeUpdateS2CPacket(this.getTime(), this.getTimeOfDay(), doDaylightCycle);
        this.getServer().getPlayerManager().sendToDimension(packet, this.getRegistryKey());
    
        // Simulate passage of time, if desired by user.
        boolean shouldTickBlockEntities = SleepWarp.getConfig().getOrDefault("tickBlockEntities", false);
        boolean shouldTickChunks = SleepWarp.getConfig().getOrDefault("tickChunks", false);
        
        if (shouldTickChunks | shouldTickBlockEntities) {
            while (ticksAdded > 0) {
                if (shouldTickChunks) {
                    var chunkManager = this.getChunkManager();
                    chunkManager.tick(shouldKeepTicking, true);
                }
                
                if (shouldTickBlockEntities)
                    this.tickBlockEntities();
                
                --ticksAdded;
            }
        }
        
        // Wake players if not night.
        if (this.worldProperties.getTimeOfDay() % 24000 < 12542) {
            if (this.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE) && this.isRaining()) {
                this.resetWeather();
            }
            
            this.wakeSleepingPlayers();
        }
    }
    
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/SleepManager;canSkipNight(I)Z"))
    private boolean suppressVanillaSleep(SleepManager instance, int percentage) {
        return false;
    }
}
