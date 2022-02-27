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
        return """
                # Maximum amount of ticks that can be added to the time every server tick. In other words: the max speed of the time warp.
                # Valid: 1 - 2147483647 | Default: 60
                maxTimeAdded=60
                
                # Scales time warp speed in relation to the amount of players sleeping.
                # Lower values will require more players to bring the warp up to full speed, and vice versa.
                # Valid: 0.1 - 1.0 | Default: 0.2
                accelerationCurve=0.2
                
                # When true, the 'playersSleepingPercentage' gamerule will be respected and a percentage of players must sleep to begin the time warp.
                # When false, only one player will be required to begin accelerating time.
                # Valid: true, false | Default: false
                useSleepPercentage=false
                
                # When true, block entities (e.g. furnaces, spawners, pistons) will be ticked at the same rate as the time warp to simulate the passage of time.
                # This feature can cause performance issues during the time warp, especially in worlds with high amounts of block entities.
                # Valid: true, false | Default: false
                tickBlockEntities=true
                
                # When true, chunks will be ticked at the same rate as the time warp to simulate the passage of time. This is a demanding feature since a chunk tick
                # is responsible for most of the world simulation (e.g. crop growth, fire spread, mob spawns). Servers with high player counts or high chunk view
                # distances should decrease maxTimeAdded and download Lithium (https://modrinth.com/mod/lithium) for the best performance with this feature enabled.
                # Valid: true, false | Default: false
                tickChunks=true
                """;
    }
}
