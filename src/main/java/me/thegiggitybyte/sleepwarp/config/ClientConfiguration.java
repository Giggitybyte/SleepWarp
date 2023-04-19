package me.thegiggitybyte.sleepwarp.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl.api.ConfigCategory;
import dev.isxander.yacl.api.Option;
import dev.isxander.yacl.api.OptionGroup;
import dev.isxander.yacl.api.YetAnotherConfigLib;
import dev.isxander.yacl.gui.controllers.TickBoxController;
import dev.isxander.yacl.gui.controllers.slider.DoubleSliderController;
import dev.isxander.yacl.gui.controllers.slider.IntegerSliderController;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ClientConfiguration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return (parentScreen) -> YetAnotherConfigLib.createBuilder()
                .title(Text.literal("SleepWarp"))
                .save(() -> JsonConfiguration.getUserInstance().writePendingChanges())
                
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Settings"))
                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("General"))
                                .option(warpSpeed())
                                .option(actionBarMessages())
                                .build())
                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("LAN Multiplayer"))
                                .tooltip(Text.literal("Options which are only effective in multiplayer LAN worlds."))
                                .option(playerMultiplier())
                                .option(useSleepingPercentage())
                                .build()
                        )
                        .build()
                )
                
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("World Simulation"))
                        .tooltip(Text.literal("Options which speed up parts of the world to simulate the passage of time."))
                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Blocks"))
                                .option(tickRandomBlock())
                                .option(tickBlockEntities())
                                .option(tickSnowAccumulation())
                                .option(tickIceFreezing())
                                .build())
                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Mobs"))
                                .option(tickAnimals())
                                // .option(animalTickMultiplier())
                                .option(tickMonsters())
                                // .option(monsterTickMultiplier())
                                .build())
                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Weather"))
                                .option(tickLightning())
                                .build())
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
                .tooltip(Text.literal("Display notifications and sleep status in the action bar."))
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("action_bar_messages").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("action_bar_messages").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("action_bar_message", value)
                )
                .controller(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Double> playerMultiplier() {
        return Option.createBuilder(double.class)
                .name(Text.literal("Player Multiplier"))
                .tooltip(Text.literal("Scales time warp speed based off of the percentage of players sleeping."))
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("player_multiplier").getAsDouble(),
                        () -> JsonConfiguration.getUserInstance().getValue("player_multiplier").getAsDouble(),
                        value -> JsonConfiguration.getUserInstance().setValue("player_multiplier", value)
                )
                .controller(doubleOption -> new DoubleSliderController(doubleOption, 0.1, 1.0, 0.05))
                .build();
    }
    
    private static Option<Boolean> useSleepingPercentage() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Use Sleeping Percentage"))
                .tooltip(
                        Text.literal("Whether or not to respect the ").append(Text.literal("playersSleepingPercentage").formatted(Formatting.ITALIC)).append(Text.literal(" gamerule.")),
                        Text.empty(),
                        Text.literal("If enabled a percentage of players would need to be asleep before the time warp can begin."),
                        Text.literal("Otherwise, only one player would be required to begin accelerating time.")
                )
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("use_sleep_percentage").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("use_sleep_percentage").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("use_sleep_percentage", value)
                )
                .controller(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Boolean> tickBlockEntities() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Tick Block Entities"))
                .tooltip(Text.literal("Block entities (e.g. furnaces, pistons, hoppers) will be ticked at the same rate as time."))
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_block_entities").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_block_entities").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_block_entities", value)
                )
                .controller(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Boolean> tickRandomBlock() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Tick Random Block"))
                .tooltip(
                        Text.literal("Random block ticks will occur more often while sleeping."),
                        Text.literal("Among other things this will cause crops, sugar cane, and saplings to grow.")
                )
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_random_block").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_random_block").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_random_block", value)
                )
                .controller(booleanOption -> new TickBoxController(booleanOption))
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
                .controller(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Boolean> tickIceFreezing() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Tick Ice Freezing"))
                .tooltip(Text.literal("Water will attempt to turn into ice faster in cold biomes when sleeping."))
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_ice_freezing").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_ice_freezing").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_ice_freezing", value)
                )
                .controller(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Boolean> tickLightning() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Tick Lightning"))
                .tooltip(Text.literal("During a thunderstorm, lightning will attempt to strike more often while asleep."))
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_lightning").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_lightning").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_lightning", value)
                )
                .controller(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Boolean> tickMonsters() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Tick Monsters"))
                .tooltip(Text.literal("Hostile mobs will move faster and do more actions while sleeping."))
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_monsters").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_monsters").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_monsters", value)
                )
                .controller(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Double> monsterTickMultiplier() {
        return Option.createBuilder(double.class)
                .name(Text.literal("Monster Tick Multiplier"))
                .tooltip(Text.literal("Scales the amount of times hostile mobs are ticked."))
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("monster_tick_multiplier").getAsDouble(),
                        () -> JsonConfiguration.getUserInstance().getValue("monster_tick_multiplier").getAsDouble(),
                        value -> JsonConfiguration.getUserInstance().setValue("monster_tick_multiplier", value)
                )
                .controller(doubleOption -> new DoubleSliderController(doubleOption, 0.1, 1.0, 0.05))
                .build();
    }
    
    private static Option<Boolean> tickAnimals() {
        return Option.createBuilder(boolean.class)
                .name(Text.literal("Tick Animals"))
                .tooltip(Text.literal("Passive animals will move faster and do more actions while sleeping."))
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("tick_animals").getAsBoolean(),
                        () -> JsonConfiguration.getUserInstance().getValue("tick_animals").getAsBoolean(),
                        value -> JsonConfiguration.getUserInstance().setValue("tick_animals", value)
                )
                .controller(booleanOption -> new TickBoxController(booleanOption))
                .build();
    }
    
    private static Option<Double> animalTickMultiplier() {
        return Option.createBuilder(double.class)
                .name(Text.literal("Animal Tick Multiplier"))
                .tooltip(Text.literal("Scales the amount of times animals are ticked."))
                .binding(
                        JsonConfiguration.getDefaultInstance().getValue("animal_tick_multiplier").getAsDouble(),
                        () -> JsonConfiguration.getUserInstance().getValue("animal_tick_multiplier").getAsDouble(),
                        value -> JsonConfiguration.getUserInstance().setValue("animal_tick_multiplier", value)
                )
                .controller(doubleOption -> new DoubleSliderController(doubleOption, 0.1, 1.0, 0.05))
                .build();
    }
}