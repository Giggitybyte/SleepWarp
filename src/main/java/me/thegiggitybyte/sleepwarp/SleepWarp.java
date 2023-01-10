package me.thegiggitybyte.sleepwarp;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl.api.ConfigCategory;
import dev.isxander.yacl.api.Option;
import dev.isxander.yacl.api.YetAnotherConfigLib;
import dev.isxander.yacl.gui.controllers.BooleanController;
import dev.isxander.yacl.gui.controllers.slider.DoubleSliderController;
import dev.isxander.yacl.gui.controllers.slider.IntegerSliderController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public class SleepWarp {
    public static final JsonConfiguration CONFIGURATION = new JsonConfiguration();
    public static final TickMonitor TICK_MONITOR = new TickMonitor();
    
    public static class Common implements ModInitializer {
        @Override
        public void onInitialize() {
            EntitySleepEvents.ALLOW_SLEEP_TIME.register((player, sleepingPos, vanillaResult) -> {
                var world = player.getWorld();
                var worldTime = world.getTimeOfDay() % 24000;
                
                if (vanillaResult == false && worldTime > 23000)
                    return ActionResult.SUCCESS;
                else
                    return ActionResult.PASS;
            });
        }
    }
    
    public static class Client implements ClientModInitializer, ModMenuApi {
        @Override
        public void onInitializeClient() {
            // TODO: client-side third person animation.
        }
        
        @Override
        public ConfigScreenFactory<?> getModConfigScreenFactory() {
            return screen -> YetAnotherConfigLib.createBuilder()
                    .title(Text.literal("SleepWarp"))
                    .save(CONFIGURATION::writePendingChanges)
                    
                    .category(ConfigCategory.createBuilder()
                            .name(Text.literal("General Settings"))
                            .tooltip(Text.literal("Settings which control how fast time passes"))
                            
                            .option(Option.createBuilder(int.class)
                                    .name(Text.literal("Maximum ticks added"))
                                    .tooltip(Text.literal("Maximum amount of ticks that can be added to the time every server tick. In other words: the max speed of the time warp."))
                                    .binding(60, () -> CONFIGURATION.get("max_ticks_added").getAsInt(), value -> CONFIGURATION.set("max_ticks_added", value))
                                    .controller(integerOption -> new IntegerSliderController(integerOption, 1, 100, 1))
                                    .build())
                            
                            .option(Option.createBuilder(double.class)
                                    .name(Text.literal("Player scale"))
                                    .tooltip(Text.literal("Scales time warp speed in relation to the amount of players sleeping."),
                                            Text.literal("Lower values will require more players to bring the warp up to full speed, and vice versa."))
                                    .binding(0.2, () -> CONFIGURATION.get("player_scale").getAsDouble(), value -> CONFIGURATION.set("player_scale", value))
                                    .controller(doubleOption -> new DoubleSliderController(doubleOption, 0.05, 1.0, 0.05))
                                    .build())
                            
                            .build())
                    
                    .category(ConfigCategory.createBuilder()
                            .name(Text.literal("Simulation Settings"))
                            .tooltip(Text.literal("Settings which control world simulation features"))
                            
                            .option(Option.createBuilder(boolean.class)
                                    .name(Text.literal("Tick block entities"))
                                    .tooltip(Text.literal("""
                                            When true, block entities (e.g. furnaces, spawners, pistons) will be ticked at the same rate as the time warp to simulate the passage of time.
                                            This feature can cause performance issues during the time warp, especially in worlds with high amounts of block entities.
                                            """))
                                    .binding(false, () -> CONFIGURATION.get("tick_block_entities").getAsBoolean(), value -> CONFIGURATION.set("tick_block_entities", value))
                                    .controller(BooleanController::new)
                                    .build())
                            
                            .option(Option.createBuilder(boolean.class)
                                    .name(Text.literal("Tick chunks"))
                                    .tooltip(Text.literal("""
                                            When true, chunks will be ticked at the same rate as the time warp to simulate the passage of time.
                                            This feature can cause performance issues during the time warp, especially in worlds with high amounts of block entities.
                                            A chunk tick is responsible for most of the world simulation (e.g. crop growth, fire spread, mob spawns), which can be very demanding.
                                            Servers with high player counts or high chunk view distances should decrease maxTimeAdded and download Lithium (https://modrinth.com/mod/lithium) for the best performance with this feature enabled.
                                            """))
                                    .binding(false, () -> CONFIGURATION.get("tick_chunks").getAsBoolean(), value -> CONFIGURATION.set("tick_chunks", value))
                                    .controller(BooleanController::new)
                                    .build())
                            
                            .build())
                    .build().generateScreen(screen);
        }
    }
}
