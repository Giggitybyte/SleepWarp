package me.thegiggitybyte.sleepwarp.fabric;

import me.thegiggitybyte.sleepwarp.fabriclike.SleepWarpFabricLike;
import net.fabricmc.api.ModInitializer;

public class SleepWarpFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SleepWarpFabricLike.init();
    }
}
