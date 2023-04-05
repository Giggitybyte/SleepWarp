package me.thegiggitybyte.sleepwarp;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class MinecraftCommands {
    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var sleepWarpCommand = dispatcher.register(literal("sleepwarp"));
            dispatcher.register(literal("sleep").redirect(sleepWarpCommand));
            
            var statusCommand = dispatcher.register(literal("status").executes(MinecraftCommands::executeStatusCommand));
            sleepWarpCommand.addChild(statusCommand);
            
            var configKeyArg = argument("key", StringArgumentType.word()).suggests(MinecraftCommands::getConfigKeySuggestions).executes(MinecraftCommands::executeConfigCommand);
            var configValueArg = argument("value", StringArgumentType.greedyString()).suggests(MinecraftCommands::getConfigValueSuggestions).executes(MinecraftCommands::executeConfigCommand);
            var configCommand = dispatcher.register(literal("config").requires(source -> source.hasPermissionLevel(2)).then(configKeyArg.then(configValueArg)));
            
            sleepWarpCommand.addChild(configCommand);
        });
    }
    
    private static int executeStatusCommand(CommandContext<ServerCommandSource> ctx) {
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
                .append(Text.literal(String.valueOf(sleepingCount)).formatted(Formatting.GRAY))
                .append(" players sleeping: ")
                .append(playerText);
        
        ctx.getSource().sendFeedback(messageText, false);
        return Command.SINGLE_SUCCESS;
    }
    
    private static int executeConfigCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var keyString = StringArgumentType.getString(ctx, "key");
        var valueString = StringArgumentType.getString(ctx, "value");
        MutableText messageText = null;
        String setValueString = "";
        
        if (JsonConfiguration.getDefaultJson().keySet().contains(keyString)) {
            var valuePrimitive = JsonConfiguration.getDefaultJson().get(keyString).getAsJsonPrimitive();
            
            if (valuePrimitive.isString()) {
                SleepWarp.USER_CONFIGURATION.set(keyString, valueString);
                setValueString = valueString;
                
            } else if (valuePrimitive.isBoolean()) {
                if (valueString.equals("true") | valueString.equals("false")) {
                    var valueBoolean = Boolean.parseBoolean(valueString);
                    SleepWarp.USER_CONFIGURATION.set(keyString, valueBoolean);
                    setValueString = String.valueOf(valueBoolean);
                } else {
                    messageText = Text.literal("Configuration key '").append(keyString).append("' expects a boolean (true or false)");
                }
                
            } else if (valuePrimitive.isNumber()) {
                try {
                    var valueInt = Integer.parseInt(valueString);
                    SleepWarp.USER_CONFIGURATION.set(keyString, valueInt);
                    setValueString = String.valueOf(valueInt);
                } catch (NumberFormatException intException) {
                    try {
                        var valueDouble = Double.parseDouble(valueString);
                        SleepWarp.USER_CONFIGURATION.set(keyString, valueDouble);
                        setValueString = String.valueOf(valueDouble);
                    } catch (NumberFormatException doubleException) {
                        messageText = Text.literal("Configuration key '").append(keyString).append("' expects an integer or float number");
                    }
                }
            } else {
                throw new SimpleCommandExceptionType(Text.literal("Unhandled value type")).create();
            }
        } else {
            messageText = Text.empty()
                    .append(Text.literal("✖ ").formatted(Formatting.RED))
                    .append(Text.literal(keyString).formatted(Formatting.DARK_RED))
                    .append(Text.literal(" is an invalid configuration key.").formatted(Formatting.RED));
        }
        
        if (messageText == null) {
            SleepWarp.USER_CONFIGURATION.writePendingChanges();
            
            messageText = Text.empty()
                    .append(Text.literal("✔ ").formatted(Formatting.GREEN))
                    .append(Text.literal(keyString + " set to ").formatted(Formatting.WHITE))
                    .append(Text.literal(setValueString).formatted(Formatting.YELLOW));
        }
        
        ctx.getSource().sendFeedback(messageText, false);
        return Command.SINGLE_SUCCESS;
    }
    
    private static CompletableFuture<Suggestions> getConfigKeySuggestions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        for (var key : JsonConfiguration.getDefaultJson().keySet())
            builder.suggest(key, Text.literal("key"));
        
        return builder.buildFuture();
    }
    
    private static CompletableFuture<Suggestions> getConfigValueSuggestions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        var keyString = ctx.getLastChild().getArgument("key", String.class);
        
        if (JsonConfiguration.getDefaultJson().has(keyString)) {
            String suggestion;
            
            if (SleepWarp.USER_CONFIGURATION.has(keyString))
                suggestion = SleepWarp.USER_CONFIGURATION.get(keyString).getAsString();
            else
                suggestion = JsonConfiguration.getDefaultJson().get(keyString).getAsJsonPrimitive().getAsString();
            
            var pendingValue = builder.getRemaining();
            if (pendingValue.length() == 0 || pendingValue.equals(suggestion))
                builder.suggest(suggestion, Text.literal("value"));
        }
        
        return builder.buildFuture();
    }
}
