package me.thegiggitybyte.sleepwarp;

import me.thegiggitybyte.sleepwarp.config.JsonConfiguration;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

public class SleepWarp {
    public static class Common implements ModInitializer {
        @Override
        public void onInitialize() {
            JsonConfiguration.getUserInstance();
            Commands.register();
            WarpEngine.initialize();
        }
    }
    
    public static class Client implements ClientModInitializer {
        @Override
        public void onInitializeClient() {
            // TODO: client-side third person animation.
        }
    }
}
