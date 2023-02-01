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
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static net.minecraft.server.command.CommandManager.literal;

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
            
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
                var sleepCommand = literal("sleep")
                        .then(literal("status").executes(ctx -> {
                            var players = ctx.getSource().getWorld().getPlayers();
                            var sleepingCount = 0;
    
                            var playerText = Text.empty();
                            for (var player : players) {
                                playerText.append(Text.literal("[").formatted(Formatting.GRAY));
                                
                                if (player.isSleeping() && player.getSleepTimer() >= 100) {
                                    playerText.append(Text.literal("✔ ").append(player.getDisplayName()).formatted(Formatting.DARK_GREEN));
                                    ++sleepingCount;
                                } else
                                    playerText.append(Text.literal("✖ ").append(player.getDisplayName()).formatted(Formatting.RED));
    
                                playerText.append(Text.literal("]").formatted(Formatting.GRAY)).append(" ");
                            }
    
                            var messageText = Text.empty()
                                    .append(Text.literal(String.valueOf(sleepingCount)).formatted(Formatting.BOLD))
                                    .append(" players sleeping: ")
                                    .append(playerText);
    
                            players.forEach(player -> player.sendMessage(messageText));
    
                            return 0;
                        }));
                
                dispatcher.register(sleepCommand);
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
                    .save(() -> CONFIGURATION.writePendingChanges())
                    
                    .category(ConfigCategory.createBuilder()
                            .name(Text.literal("General Settings"))
                            
                            .option(Option.createBuilder(int.class)
                                    .name(Text.literal("Speed"))
                                    .tooltip(Text.literal("Controls how fast time will pass while sleeping."))
                                    .binding(3,
                                            () -> BigDecimal.valueOf(CONFIGURATION.get("max_ticks_added").getAsInt()).divide(BigDecimal.valueOf(20), RoundingMode.HALF_UP).intValue(),
                                            value -> CONFIGURATION.set("max_ticks_added", value * 20))
                                    .controller(integerOption -> new IntegerSliderController(integerOption, 1, 10, 1, speed -> Text.literal("Warp " + speed)))
                                    .build())
                            
                            .option(Option.createBuilder(boolean.class)
                                    .name(Text.literal("Action Bar Message"))
                                    .tooltip(Text.literal("Display the time remaining in the action bar."))
                                    .binding(true, () -> CONFIGURATION.get("action_bar_message").getAsBoolean(), value -> CONFIGURATION.set("action_bar_message", value))
                                    .controller(booleanOption -> new BooleanController(booleanOption, value -> Text.literal(value ? "Enabled" : "Disabled"), false))
                                    .build())
                            
                            .option(Option.createBuilder(boolean.class)
                                    .name(Text.literal("Use Sleeping Percentage"))
                                    .tooltip(Text.literal("Whether or not to respect the ").append(Text.literal("playersSleepingPercentage").formatted(Formatting.ITALIC)).append(Text.literal(" gamerule.")).append("\n").append("\n")
                                                    .append(Text.literal("YES").formatted(Formatting.BOLD)).append(": A percentage of players would need to be asleep before the time warp can begin.").append("\n")
                                                    .append(Text.literal("NO").formatted(Formatting.BOLD)).append(": Only one player would be required to begin accelerating time."),
                                            Text.empty(),
                                            Text.literal("This setting is only effective on LAN worlds."))
                                    .binding(false, () -> CONFIGURATION.get("use_sleep_percentage").getAsBoolean(), value -> CONFIGURATION.set("action_bar_message", value))
                                    .controller(booleanOption -> new BooleanController(booleanOption, value -> Text.literal(value ? "Yes " : "No"), false))
                                    .build())
                            
                            .option(Option.createBuilder(double.class)
                                    .name(Text.literal("Player Scale"))
                                    .tooltip(Text.literal("Scales time warp speed based off of the percentage of players sleeping. " +
                                                          "Higher percentage will require more players to bring the warp to full speed, and vice versa."),
                                            Text.empty(),
                                            Text.literal("This setting is only effective for LAN worlds."))
                                    .binding(0.8, () -> 1.0 - CONFIGURATION.get("player_scale").getAsDouble(), value -> CONFIGURATION.set("player_scale", 1.0 - value))
                                    .controller(doubleOption -> new DoubleSliderController(doubleOption, 0.1, 1.0, 0.1, value -> {
                                        var percentage = BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
                                        return Text.literal(percentage + "%");
                                    }))
                                    .build())
                            
                            .build())
                    
                    .category(ConfigCategory.createBuilder()
                            .name(Text.literal("Simulation Settings"))
                            
                            .option(Option.createBuilder(boolean.class)
                                    .name(Text.literal("Tick Block Entities"))
                                    .tooltip(Text.literal("Block entities (e.g. furnaces, spawners, pistons) will be ticked at the same speed as the time warp to simulate the passage of time. " +
                                                          "This feature can cause performance issues during the time warp, especially in worlds with high amounts of block entities."))
                                    .binding(false, () -> CONFIGURATION.get("tick_block_entities").getAsBoolean(), value -> CONFIGURATION.set("tick_block_entities", value))
                                    .controller(booleanOption -> new BooleanController(booleanOption, value -> Text.literal(value ? "Enabled" : "Disabled"), false))
                                    .build())
                            
                            .option(Option.createBuilder(boolean.class)
                                    .name(Text.literal("Tick Chunks"))
                                    .tooltip(Text.literal("Chunks will be ticked at the same speed as the time warp to simulate the passage of time. " +
                                                          "A chunk tick is responsible for most of the world simulation (e.g. crop growth, fire spread, mob spawns), which can be strenuous for your computer. " +
                                                          "Decreasing the speed of the time warp and installing the Lithium mod (from CaffeineMC) will result in the best performance with this feature enabled."))
                                    .binding(false, () -> CONFIGURATION.get("tick_chunks").getAsBoolean(), value -> CONFIGURATION.set("tick_chunks", value))
                                    .controller(booleanOption -> new BooleanController(booleanOption, value -> Text.literal(value ? "Enabled" : "Disabled"), false))
                                    .build())
                            
                            .option(Option.createBuilder(boolean.class)
                                    .name(Text.literal("Performance Mode"))
                                    .tooltip(Text.literal("Scales maximum warp speed based on the average TPS during the time warp. " +
                                                          "World simulation features above will be disabled dynamically during each tick when the average begins to drop, and enabled when the average returns to normal."))
                                    .binding(false, () -> CONFIGURATION.get("performance_mode").getAsBoolean(), value -> CONFIGURATION.set("performance_mode", value))
                                    .controller(booleanOption -> new BooleanController(booleanOption, value -> Text.literal(value ? "Enabled" : "Disabled"), true))
                                    .build())
                            
                            .build())
                    .build().generateScreen(screen);
        }
    }
}
