package me.thegiggitybyte.sleepwarp.quilt;

import me.thegiggitybyte.sleepwarp.fabriclike.SleepWarpFabricLike;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;

public class SleepWarpQuilt implements ModInitializer {
    @Override
    public void onInitialize(ModContainer mod) {
        SleepWarpFabricLike.init();
    }
}
