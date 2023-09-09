package me.thegiggitybyte.sleepwarp.mixin.common;

import me.thegiggitybyte.sleepwarp.config.JsonConfiguration;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.SleepManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.GameRules;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin extends World {
    protected ServerWorldMixin(MutableWorldProperties properties, RegistryKey<World> registryRef, DynamicRegistryManager registryManager, RegistryEntry<DimensionType> dimensionEntry, Supplier<Profiler> profiler, boolean isClient, boolean debugWorld, long biomeAccess, int maxChainedNeighborUpdates) {
        super(properties, registryRef, registryManager, dimensionEntry, profiler, isClient, debugWorld, biomeAccess, maxChainedNeighborUpdates);
    }
    
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/SleepManager;canSkipNight(I)Z"))
    private boolean suppressVanillaSleep(SleepManager instance, int percentage) {
        return false;
    }
    
    @Redirect(method = "updateSleepingPlayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;sendSleepingStatus()V"))
    private void sendWarpStatus(ServerWorld world) {
        if (world.getServer().isSingleplayer() || !world.getServer().isRemote() || world.getPlayers().size() == 1) return;
        if (!JsonConfiguration.getUserInstance().getValue("action_bar_messages").getAsBoolean()) return;
        
        long playerCount = 0, inBedCount = 0, sleepingCount = 0;
        
        for (var player : world.getPlayers()) {
            if (player.isSleeping()) {
                if (player.getSleepTimer() >= 100) ++sleepingCount;
                ++inBedCount;
            }
            
            ++playerCount;
        }
        
        var messageText = Text.empty();
        var tallyText = Text.empty()
                .append(Text.literal(String.valueOf(inBedCount)))
                .append("/")
                .append(Text.literal(String.valueOf(playerCount)));
        
        if (inBedCount == 0) {
            messageText.append(tallyText.formatted(Formatting.DARK_GRAY)).append(" players sleeping");
        } else if (JsonConfiguration.getUserInstance().getValue("use_sleep_percentage").getAsBoolean()) {
            var percentRequired = world.getGameRules().getInt(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
            var minSleepingCount = Math.max(1, (playerCount * percentRequired) / 100);
            
            if (sleepingCount < minSleepingCount && minSleepingCount - inBedCount > 0) {
                messageText.append(tallyText.formatted(Formatting.RED))
                        .append(" players sleeping. ")
                        .append(String.valueOf((minSleepingCount - inBedCount)))
                        .append(" more needed to advance time.");
            } else {
                messageText.append(tallyText.formatted(Formatting.DARK_GREEN)).append(" players sleeping");
            }
        } else if (sleepingCount == 0) {
            messageText.append(tallyText.formatted(Formatting.YELLOW)).append(" players sleeping");
        }
        
        if (messageText.getSiblings().size() > 0) {
            world.getPlayers().forEach(player -> player.sendMessage(messageText, true));
        }
    }
}
