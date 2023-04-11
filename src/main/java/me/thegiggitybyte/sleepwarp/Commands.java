package me.thegiggitybyte.sleepwarp;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.thegiggitybyte.sleepwarp.config.JsonConfiguration;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Commands {
    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var sleepWarpCommand = dispatcher.register(literal("sleepwarp"));
            dispatcher.register(literal("sleep").redirect(sleepWarpCommand));
            
            var statusCommand = literal("status").executes(Commands::executeStatusCommand).build();
            sleepWarpCommand.addChild(statusCommand);
            
            var configKeyArg = argument("key", StringArgumentType.word()).suggests(Commands::getConfigKeySuggestions);
            var configValueArg = argument("value", StringArgumentType.greedyString()).suggests(Commands::getConfigValueSuggestions).executes(Commands::executeConfigCommand);
            var configCommand = literal("config").requires(source -> source.hasPermissionLevel(2)).then(configKeyArg.then(configValueArg)).build();
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
    
    // TODO: BETTER INPUT VALIDATION
    private static int executeConfigCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var keyString = StringArgumentType.getString(ctx, "key");
        var valueString = StringArgumentType.getString(ctx, "value");
        MutableText messageText = null;
        String setValueString = "";
        
        if (JsonConfiguration.getDefaultInstance().hasKey(keyString)) {
            var valuePrimitive = JsonConfiguration.getDefaultInstance().getValue(keyString);
            
            if (valuePrimitive.isString()) {
                JsonConfiguration.getUserInstance().setValue(keyString, valueString);
                setValueString = valueString;
                
            } else if (valuePrimitive.isBoolean()) {
                if (valueString.equals("true") | valueString.equals("false")) {
                    var valueBoolean = Boolean.parseBoolean(valueString);
                    JsonConfiguration.getUserInstance().setValue(keyString, valueBoolean);
                    setValueString = String.valueOf(valueBoolean);
                } else {
                    messageText = Text.literal("Configuration key '").append(keyString).append("' expects a boolean (true or false)");
                }
                
            } else if (valuePrimitive.isNumber()) {
                try {
                    var valueInt = Integer.parseInt(valueString);
                    JsonConfiguration.getUserInstance().setValue(keyString, valueInt);
                    setValueString = String.valueOf(valueInt);
                } catch (NumberFormatException intException) {
                    try {
                        var valueDouble = Double.parseDouble(valueString);
                        JsonConfiguration.getUserInstance().setValue(keyString, valueDouble);
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
            JsonConfiguration.getUserInstance().writePendingChanges();
            
            messageText = Text.empty()
                    .append(Text.literal("✔ ").formatted(Formatting.GREEN))
                    .append(Text.literal(keyString + " set to ").formatted(Formatting.WHITE))
                    .append(Text.literal(setValueString).formatted(Formatting.YELLOW));
        }
        
        ctx.getSource().sendFeedback(messageText, false);
        return Command.SINGLE_SUCCESS;
    }
    
    private static CompletableFuture<Suggestions> getConfigKeySuggestions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        for (var key : JsonConfiguration.getDefaultInstance().getKeys())
            builder.suggest(key, Text.literal("key"));
        
        return builder.buildFuture();
    }
    
    private static CompletableFuture<Suggestions> getConfigValueSuggestions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        var keyString = ctx.getLastChild().getArgument("key", String.class);
        
        if (JsonConfiguration.getDefaultInstance().hasKey(keyString)) {
            String suggestion;
            
            if (JsonConfiguration.getUserInstance().hasKey(keyString))
                suggestion = JsonConfiguration.getUserInstance().getValue(keyString).getAsString();
            else
                suggestion = JsonConfiguration.getUserInstance().getValue(keyString).getAsString();
            
            var pendingValue = builder.getRemaining();
            if (pendingValue.length() == 0 || pendingValue.equals(suggestion))
                builder.suggest(suggestion, Text.literal("value"));
        }
        
        return builder.buildFuture();
    }
}
