package me.thegiggitybyte.sleepwarp.mixin;

import net.minecraft.server.world.SleepManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SleepManager.class)
public interface SleepManagerAccessor {
    @Accessor("total")
    int getTotal();
    
    @Accessor("sleeping")
    int getSleeping();
}
