package me.thegiggitybyte.sleepwarp.config;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.gui.controllers.TickBoxController;
import dev.isxander.yacl3.gui.controllers.slider.DoubleSliderController;
import dev.isxander.yacl3.gui.controllers.slider.IntegerSliderController;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClientConfiguration {
    public static Screen create(Screen parentScreen) {
        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("SleepWarp"))
                .save(() -> JsonConfiguration.getUserInstance().writePendingChanges())
                
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Settings"))
                        .group(OptionGroup.createBuilder()
                                .name(Component.literal("General"))
                                .option(warpSpeed())
                                .option(actionBarMessages())
                                .build())
                        .group(OptionGroup.createBuilder()
                                .name(Component.literal("LAN Multiplayer"))
                                .description(OptionDescription.createBuilder().text(Component.literal("Options which are only effective in multiplayer LAN worlds.")).build())
                                .option(playerMultiplier())
                                .option(useSleepingPercentage())
                                .build()
                        )
                        .build()
                )
                
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("World Simulation"))
                        .tooltip(Component.literal("Options which speed up parts of the world to simulate the passage of time."))
                        .group(OptionGroup.createBuilder()
                                .name(Component.literal("Blocks"))
                                .option(tickRandomBlock())
                                .option(tickBlockEntities())
                                .option(tickSnowAccumulation())
                                .option(tickIceFreezing())
                                .build())
                        .group(OptionGroup.createBuilder()
                                .name(Component.literal("Mobs"))
                                .option(tickAnimals())
                                .option(animalTickMultiplier())
                                .option(tickMonsters())
                                .option(monsterTickMultiplier())
                                .build())
                        .group(OptionGroup.createBuilder()
                                .name(Component.literal("Weather"))
                                .option(tickLightning())
                                .build())
                        .build()
                )
                
                .build()
                .generateScreen(parentScreen);
    }
    
    private static Option<Integer> warpSpeed() {
        return Option.createBuilder(int.class)
                .name(Component.literal("Speed"))
                .description(OptionDescription.createBuilder().text(Component.literal("Controls how fast time will pass while sleeping.")).build())
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("max_ticks_added").getAsInt(),
                        () -> JsonConfiguration.getUserInstance().getValue("max_ticks_added").getAsInt(),
                        value -> JsonConfiguration.getUserInstance().setValue("max_ticks_added", value)
                )
                .customController(integerOption -> new IntegerSliderController(integerOption, 10, 100, 10, speed -> Component.literal("Warp " + speed / 10)))
                .build();
    }
    
    private static Option<Boolean> actionBarMessages() {
        return Option.createBuilder(boolean.class)
                .name(Component.literal("Action Bar Messages"))
                .description(OptionDescription.createBuilder().text(Component.literal("Display notifications and sleep status in the action bar.")).build())
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("action_bar_messages").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("action_bar_messages").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("action_bar_message", value)
                )
                .customController(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Double> playerMultiplier() {
        return Option.createBuilder(double.class)
                .name(Component.literal("Player Multiplier"))
                .description(OptionDescription.createBuilder().text(Component.literal("Scales time warp speed based off of the percentage of players sleeping.")).build())
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("player_multiplier").getAsDouble(),
                        () -> JsonConfiguration.getUserInstance().getValue("player_multiplier").getAsDouble(),
                        value -> JsonConfiguration.getUserInstance().setValue("player_multiplier", value)
                )
                .customController(doubleOption -> new DoubleSliderController(doubleOption, 0.1, 1.0, 0.05))
                .build();
    }
    
    private static Option<Boolean> useSleepingPercentage() {
        return Option.createBuilder(boolean.class)
                .name(Component.literal("Use Sleeping Percentage"))
                .description(OptionDescription.createBuilder().text(
                        Component.literal("Whether or not to respect the ").append(Component.literal("playersSleepingPercentage").withStyle(ChatFormatting.ITALIC)).append(Component.literal(" gamerule.")),
                        Component.empty(),
                        Component.literal("If enabled a percentage of players would need to be asleep before the time warp can begin."),
                        Component.literal("Otherwise, only one player would be required to begin accelerating time.")
                ).build())
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("use_sleep_percentage").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("use_sleep_percentage").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("use_sleep_percentage", value)
                )
                .customController(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Boolean> tickBlockEntities() {
        return Option.createBuilder(boolean.class)
                .name(Component.literal("Tick Block Entities"))
                .description(OptionDescription.createBuilder().text(Component.literal("Block entities (e.g. furnaces, pistons, hoppers) will be ticked at the same rate as time.")).build())
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_block_entities").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_block_entities").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_block_entities", value)
                )
                .customController(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Boolean> tickRandomBlock() {
        return Option.createBuilder(boolean.class)
                .name(Component.literal("Tick Random Block"))
                
                .description(OptionDescription.createBuilder().text(
                        Component.literal("Random block ticks will occur more often while sleeping."),
                        Component.literal("Among other things this will cause crops, sugar cane, and saplings to grow.")
                ).build())
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_random_block").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_random_block").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_random_block", value)
                )
                .customController(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Boolean> tickSnowAccumulation() {
        return Option.createBuilder(boolean.class)
                .name(Component.literal("Tick Snow Accumulation"))
                .description(OptionDescription.createBuilder().text(Component.literal("Snow layers will pile up faster in cold biomes when it is snowing.")).build())
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_snow_accumulation").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_snow_accumulation").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_snow_accumulation", value)
                )
                .customController(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Boolean> tickIceFreezing() {
        return Option.createBuilder(boolean.class)
                .name(Component.literal("Tick Ice Freezing"))
                .description(OptionDescription.createBuilder().text(Component.literal("Water will attempt to turn into ice faster in cold biomes when sleeping.")).build())
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_ice_freezing").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_ice_freezing").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_ice_freezing", value)
                )
                .customController(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Boolean> tickLightning() {
        return Option.createBuilder(boolean.class)
                .name(Component.literal("Tick Lightning"))
                .description(OptionDescription.createBuilder().text(Component.literal("During a thunderstorm, lightning will attempt to strike more often while asleep.")).build())
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_lightning").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_lightning").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_lightning", value)
                )
                .customController(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Boolean> tickMonsters() {
        return Option.createBuilder(boolean.class)
                .name(Component.literal("Tick Monsters"))
                .description(OptionDescription.createBuilder().text(Component.literal("Hostile mobs will move faster and do more actions while sleeping.")).build())
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_monsters").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_monsters").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_monsters", value)
                )
                .customController(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Double> monsterTickMultiplier() {
        return Option.createBuilder(double.class)
                .name(Component.literal("Monster Tick Multiplier"))
                .description(OptionDescription.createBuilder().text(Component.literal("Scales the amount of times hostile mobs are ticked.")).build())
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("monster_tick_multiplier").getAsDouble(),
                        () -> JsonConfiguration.getUserInstance().getValue("monster_tick_multiplier").getAsDouble(),
                        value -> JsonConfiguration.getUserInstance().setValue("monster_tick_multiplier", value)
                )
                .customController(doubleOption -> new DoubleSliderController(doubleOption, 0.1, 1.0, 0.05))
                .build();
    }
    
    private static Option<Boolean> tickAnimals() {
        return Option.createBuilder(boolean.class)
                .name(Component.literal("Tick Animals"))
                .description(OptionDescription.createBuilder().text(Component.literal("Passive animals will move faster and do more actions while sleeping.")).build())
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_animals").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_animals").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_animals", value)
                )
                .customController(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Double> animalTickMultiplier() {
        return Option.createBuilder(double.class)
                .name(Component.literal("Animal Tick Multiplier"))
                .description(OptionDescription.createBuilder().text(Component.literal("Scales the amount of times animals are ticked.")).build())
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("animal_tick_multiplier").getAsDouble(),
                        () -> JsonConfiguration.getUserInstance().getValue("animal_tick_multiplier").getAsDouble(),
                        value -> JsonConfiguration.getUserInstance().setValue("animal_tick_multiplier", value)
                )
                .customController(doubleOption -> new DoubleSliderController(doubleOption, 0.1, 1.0, 0.05))
                .build();
    }
}
