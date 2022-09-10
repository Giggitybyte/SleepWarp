package me.thegiggitybyte.sleepwarp;

import net.darktree.simpleconfig.SimpleConfig;
import net.fabricmc.api.ModInitializer;

public class SleepWarp implements ModInitializer {
    private static SimpleConfig config;
    
    @Override
    public void onInitialize() {
        config = SimpleConfig
                .of("sleepwarp")
                .provider(this::getDefaultConfig)
                .request();
    }
    
    public static SimpleConfig getConfig() {
        return config;
    }
    
    private String getDefaultConfig(String fileName) {
        return "# Maximum amount of ticks that can be added to the time every server tick. In other words: the max speed of the time warp.\n" +
               "# Valid: 1 - 2147483647 | Default: 60\n" +
               "maxTimeAdded=60\n" +
               "\n" +
               "# Scales time warp speed in relation to the amount of players sleeping.\n" +
               "# Lower values will require more players to bring the warp up to full speed, and vice versa.\n" +
               "# Valid: 0.1 - 1.0 | Default: 0.2\n" +
               "accelerationCurve=0.2\n" +
               "\n" +
               "# When true, block entities (e.g. furnaces, spawners, pistons) will be ticked at the same rate as the time warp to simulate the passage of time.\n" +
               "# This feature can cause performance issues during the time warp, especially in worlds with high amounts of block entities.\n" +
               "# Valid: true, false | Default: false\n" +
               "tickBlockEntities=false\n" +
               "\n" +
               "# When true, chunks will be ticked at the same rate as the time warp to simulate the passage of time. This is a demanding feature since a chunk tick\n" +
               "# is responsible for most of the world simulation (e.g. crop growth, fire spread, mob spawns). Servers with high player counts or high chunk view\n" +
               "# distances should decrease maxTimeAdded and download Lithium (https://modrinth.com/mod/lithium) for the best performance with this feature enabled.\n" +
               "# Valid: true, false | Default: false\n" +
               "tickChunks=false";
    }
}
