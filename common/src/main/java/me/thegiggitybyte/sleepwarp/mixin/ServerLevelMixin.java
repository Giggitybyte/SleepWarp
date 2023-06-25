package me.thegiggitybyte.sleepwarp.mixin;

import me.thegiggitybyte.sleepwarp.config.JsonConfiguration;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.SleepManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.GameRules;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level {
    protected ServerLevelMixin(WritableLevelData writableLevelData, ResourceKey<Level> resourceKey, RegistryAccess registryAccess, Holder<DimensionType> holder, Supplier<ProfilerFiller> supplier, boolean bl, boolean bl2, long l, int i) {
        super(writableLevelData, resourceKey, registryAccess, holder, supplier, bl, bl2, l, i);
    }
    
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/SleepStatus;areEnoughSleeping(I)Z"))
    private boolean suppressVanillaSleep(SleepStatus instance, int i) {
        return false;
    }
    
    @Redirect(method = "updateSleepingPlayerList", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;announceSleepStatus()V"))
    private void sendWarpStatus(ServerLevel level) {
        if (level.getServer().isSingleplayer() || !level.getServer().isPublished() || level.getPlayers().size() == 1) return;
        if (JsonConfiguration.getUserInstance().getValue("action_bar_messages").getAsBoolean() == false) return;
        
        long playerCount = 0, inBedCount = 0, sleepingCount = 0;
        
        for (var player : level.getPlayers(player -> !player.isSpectator())) {
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
            var percentRequired = level.getGameRules().getInt(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
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
            level.getPlayers().forEach(player -> player.sendMessage(messageText, true));
        }
    }
}
