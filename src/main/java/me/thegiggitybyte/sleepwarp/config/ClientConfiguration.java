package me.thegiggitybyte.sleepwarp.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl.api.ConfigCategory;
import dev.isxander.yacl.api.Option;
import dev.isxander.yacl.api.YetAnotherConfigLib;
import dev.isxander.yacl.gui.controllers.BooleanController;
import dev.isxander.yacl.gui.controllers.slider.DoubleSliderController;
import dev.isxander.yacl.gui.controllers.slider.IntegerSliderController;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Function;

public class ClientConfiguration implements ModMenuApi {
    private static final Function<Boolean, Text> ENABLED_DISABLED = (value) -> value ? Text.literal("Enabled").formatted(Formatting.UNDERLINE) : Text.literal("Disabled").formatted(Formatting.GRAY);
    
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return (parentScreen) -> YetAnotherConfigLib.createBuilder()
                .title(Text.literal("SleepWarp"))
                .save(() -> JsonConfiguration.getUserInstance().writePendingChanges())
                
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("General Settings"))
                        
                        .option(warpSpeed())
                        .option(actionBarMessages())
                        .option(playerMultiplier())
                        .option(useSleepingPercentage())
                        .option(performanceMode())
                        
                        .build()
                )
                
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Simulation Settings"))
                        
                        .option(tickRandomBlock())
                        .option(tickBlockEntities())
                        .option(tickSnowAccumulation())
                        .option(tickIceFreezing())
                        .option(tickLightning())
                        .option(tickEntities())
                        .option(tickSpawners())
                        .option(tickMobSpawn())
                        
                        .build()
                )
                .build()
                .generateScreen(parentScreen);
    }
    
    private static Option<Integer> warpSpeed() {
        return Option.createBuilder(int.class)
                .name(Text.literal("Speed"))
                .tooltip(Text.literal("Controls how fast time will pass while sleeping."))
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("max_ticks_added").getAsInt(),
                        () -> JsonConfiguration.getUserInstance().getValue("max_ticks_added").getAsInt(),
                        value -> JsonConfiguration.getUserInstance().setValue("max_ticks_added", value)
                )
                .controller(integerOption -> new IntegerSliderController(integerOption, 10, 100, 10, speed -> Text.literal("Warp " + speed / 10)))
                .build();
    }
    
    private static Option<Boolean> actionBarMessages() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Action Bar Messages"))
                .tooltip(Text.literal("Display sleep information in the action bar."))
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("action_bar_messages").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("action_bar_messages").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("action_bar_message", value)
                )
                .controller(booleanOption -> new BooleanController(booleanOption, ENABLED_DISABLED, false))
                .build();
    }
    
    private static Option<Double> playerMultiplier() {
        return Option.createBuilder(double.class)
                .name(Text.literal("Player Multiplier"))
                .tooltip(
                        Text.literal("Scales time warp speed based off of the percentage of players sleeping."),
                        Text.empty(),
                        Text.literal("This setting is only effective on LAN worlds.")
                )
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("player_multiplier").getAsDouble(),
                        () -> JsonConfiguration.getUserInstance().getValue("player_multiplier").getAsDouble(),
                        value -> JsonConfiguration.getUserInstance().setValue("player_multiplier", value)
                )
                .controller(doubleOption -> {
                    return new DoubleSliderController(doubleOption, 0.1, 1.0, 0.05);
                })
                .build();
    }
    
    private static Option<Boolean> useSleepingPercentage() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Use Sleeping Percentage"))
                .tooltip(
                        Text.literal("Whether or not to respect the ").append(Text.literal("playersSleepingPercentage").formatted(Formatting.ITALIC)).append(Text.literal(" gamerule.")),
                        Text.empty(),
                        Text.literal("If enabled a percentage of players would need to be asleep before the time warp can begin."),
                        Text.literal("Otherwise, only one player would be required to begin accelerating time."),
                        Text.empty(),
                        Text.literal("This setting is only effective on LAN worlds.")
                )
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("use_sleep_percentage").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("use_sleep_percentage").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("use_sleep_percentage", value)
                )
                .controller(booleanOption -> new BooleanController(booleanOption, ENABLED_DISABLED, false))
                .build();
    }
    
    private static Option<Boolean> performanceMode() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Performance Mode"))
                .tooltip(Text.literal("Scales maximum warp speed based on the average TPS during the time warp. "),
                        Text.literal("World simulation features above will be disabled dynamically during each tick when the average begins to drop, and enabled when the average returns to normal."))
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("performance_mode").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("performance_mode").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("performance_mode", value)
                )
                .controller(booleanOption -> new BooleanController(booleanOption, ENABLED_DISABLED, false))
                .build();
    }
    
    private static Option<Boolean> tickBlockEntities() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Tick Block Entities"))
                .tooltip(
                        Text.literal("Block entities (e.g. furnaces, pistons, hoppers) will be tick at increased rate through the night."),
                        Text.literal("This feature can cause performance issues during the time warp, especially in worlds with high amounts of block entities.")
                )
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_block_entities").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_block_entities").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_block_entities", value)
                )
                .controller(booleanOption -> new BooleanController(booleanOption, ENABLED_DISABLED, false))
                .build();
    }
    
    private static Option<Boolean> tickRandomBlock() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Tick Random Block"))
                .tooltip(
                        Text.literal("Random ticks will occur at an increased rate through the night."),
                        Text.literal("Among other things this will cause crops, sugar cane and saplings to grow faster.")
                )
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_random_block").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_random_block").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_random_block", value)
                )
                .controller(booleanOption -> new BooleanController(booleanOption, ENABLED_DISABLED, false))
                .build();
    }
    
    private static Option<Boolean> tickSnowAccumulation() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Tick Snow Accumulation"))
                .tooltip(Text.literal("Snow layers will pile up faster in cold biomes when it is snowing."))
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_snow_accumulation").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_snow_accumulation").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_snow_accumulation", value)
                )
                .controller(booleanOption -> new BooleanController(booleanOption, ENABLED_DISABLED, false))
                .build();
    }
    
    private static Option<Boolean> tickIceFreezing() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Tick Ice Freezing"))
                .tooltip(Text.literal("Water will attempt to turn into ice faster in cold biomes when sleeping"))
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_ice_freezing").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_ice_freezing").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_ice_freezing", value)
                )
                .controller(booleanOption -> new BooleanController(booleanOption, ENABLED_DISABLED, false))
                .build();
    }
    
    private static Option<Boolean> tickLightning() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Tick Lightning"))
                .tooltip(Text.literal("Lightning will attempt to strike more often while sleeping."))
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_lightning").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_lightning").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_lightning", value)
                )
                .controller(booleanOption -> new BooleanController(booleanOption, ENABLED_DISABLED, false))
                .build();
    }
    
    private static Option<Boolean> tickEntities() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Tick Entities"))
                .tooltip(
                        Text.literal("Animals and mobs will move and try to do actions more often during the night."),
                        Text.literal("This option can cause lots of lag when enabled, especially at higher render distances.").formatted(Formatting.ITALIC)
                )
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_entities").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_entities").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_entities", value)
                )
                .controller(booleanOption -> new BooleanController(booleanOption, ENABLED_DISABLED, false))
                .build();
    }
    
    private static Option<Boolean> tickMobSpawn() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Tick Natural Mob Spawn"))
                .tooltip(
                        Text.literal("Peaceful and hostile mobs will attempt to spawn at an increased rate through the night."),
                        Text.literal("This option can cause lots of lag when enabled, especially at higher render distances.").formatted(Formatting.ITALIC)
                )
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_mob_spawn").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_mob_spawn").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_mob_spawn", value)
                )
                .controller(booleanOption -> new BooleanController(booleanOption, ENABLED_DISABLED, false))
                .build();
    }
    
    private static Option<Boolean> tickSpawners() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Tick Spawners"))
                .tooltip(Text.literal("Spawner blocks will attempt to spawn a entity more often when sleeping."))
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_spawners").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_spawners").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_spawners", value)
                )
                .controller(booleanOption -> new BooleanController(booleanOption, ENABLED_DISABLED, false))
                .build();
    }
}