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

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SleepWarp {
    public static final JsonConfiguration CONFIGURATION = new JsonConfiguration();
    public static final TickMonitor TICK_MONITOR = new TickMonitor();
    
    public static class Common implements ModInitializer {
        @Override
        public void onInitialize() {
            EntitySleepEvents.ALLOW_SLEEP_TIME.register((player, sleepingPos, vanillaResult) -> {
                if (vanillaResult == false && (player.getWorld().getTimeOfDay() % 24000 > 12542))
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
                            .name(Text.literal("Warp Settings"))
                            .tooltip(Text.literal("Settings which control how time passes."))
                            
                            .option(Option.createBuilder(int.class)
                                    .name(Text.literal("Speed"))
                                    .tooltip(Text.literal("Controls how fast time will pass while sleeping."))
                                    .binding(3,
                                            () -> BigDecimal.valueOf(CONFIGURATION.get("max_ticks_added").getAsInt()).divide(BigDecimal.valueOf(20), RoundingMode.HALF_UP).intValue(),
                                            value -> CONFIGURATION.set("max_ticks_added", value * 20))
                                    .controller(integerOption -> new IntegerSliderController(integerOption, 1, 10, 1, speed -> Text.literal("Warp " + speed)))
                                    .build())
                            
                            .option(Option.createBuilder(double.class)
                                    .name(Text.literal("Player Scale"))
                                    .tooltip(Text.literal("Scales time warp speed in relation to the amount of players sleeping."))
                                    .binding(0.7, () -> 1.0 - CONFIGURATION.get("player_scale").getAsDouble(), value -> CONFIGURATION.set("player_scale", 1.0 - value))
                                    .controller(doubleOption -> new DoubleSliderController(doubleOption, 0.05, 1.0, 0.05, value -> {
                                        var percentage = BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
                                        return Text.literal(percentage + "%");
                                    }))
                                    .build())
                            
                            .option(Option.createBuilder(boolean.class)
                                    .name(Text.literal("Performance Mode"))
                                    .tooltip(Text.literal("Scales the speed based on the average TPS during the time warp"),
                                            Text.literal("World simulation features will be dynamically disabled when the average begins to drop"))
                                    .binding(false, () -> CONFIGURATION.get("performance_mode").getAsBoolean(), value -> CONFIGURATION.set("performance_mode", value))
                                    .controller(booleanOption -> new BooleanController(booleanOption, value -> Text.literal(value ? "Enabled" : "Disabled"), false))
                                    .build())
                            
                            .build())
                    
                    .category(ConfigCategory.createBuilder()
                            .name(Text.literal("Simulation Settings"))
                            .tooltip(Text.literal("Settings which control world simulation features"))
                            
                            .option(Option.createBuilder(boolean.class)
                                    .name(Text.literal("Tick block entities"))
                                    .tooltip(Text.literal("""
                                            Block entities (e.g. furnaces, spawners, pistons) will be ticked at the same rate as the time warp to simulate the passage of time.
                                            This feature can cause performance issues during the time warp, especially in worlds with high amounts of block entities.
                                            """))
                                    .binding(false, () -> CONFIGURATION.get("tick_block_entities").getAsBoolean(), value -> CONFIGURATION.set("tick_block_entities", value))
                                    .controller(BooleanController::new)
                                    .build())
                            
                            .option(Option.createBuilder(boolean.class)
                                    .name(Text.literal("Tick chunks"))
                                    .tooltip(Text.literal("""
                                            Chunks will be ticked at the same rate as the time warp to simulate the passage of time.
                                            A chunk tick is responsible for most of the world simulation (e.g. crop growth, fire spread, mob spawns), which can be strenuous for your computer.
                                            Decreasing the speed of the time warp and installing the Lithium mod (from CaffeineMC) will result in the best performance with this feature enabled.
                                            """))
                                    .binding(false, () -> CONFIGURATION.get("tick_chunks").getAsBoolean(), value -> CONFIGURATION.set("tick_chunks", value))
                                    .controller(BooleanController::new)
                                    .build())
                            
                            .build())
                    .build().generateScreen(screen);
        }
    }
}
