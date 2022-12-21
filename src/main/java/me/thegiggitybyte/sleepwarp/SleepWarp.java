package me.thegiggitybyte.sleepwarp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl.api.ConfigCategory;
import dev.isxander.yacl.api.Option;
import dev.isxander.yacl.api.YetAnotherConfigLib;
import dev.isxander.yacl.gui.controllers.BooleanController;
import dev.isxander.yacl.gui.controllers.slider.DoubleSliderController;
import dev.isxander.yacl.gui.controllers.slider.IntegerSliderController;
import net.fabricmc.api.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class SleepWarp {
    public static class Server implements DedicatedServerModInitializer {
        @Override
        public void onInitializeServer() {
            Config.initialize();
        }
    }
    
    public static class Client implements ClientModInitializer, ModMenuApi {
        @Override
        public void onInitializeClient() {
            Config.initialize();
        }
        
        @Override
        public ConfigScreenFactory<?> getModConfigScreenFactory() {
            return parent -> YetAnotherConfigLib.createBuilder()
                    .title(Text.literal("SleepWarp"))
                    .save(Config::writePendingChanges)
                    
                    .category(ConfigCategory.createBuilder()
                            .name(Text.literal("Speed Settings"))
                            .tooltip(Text.literal("Settings which control how fast time passes"))
                            
                            .option(Option.createBuilder(int.class)
                                    .name(Text.literal("Maximum ticks added"))
                                    .tooltip(Text.literal("Maximum amount of ticks that can be added to the time every server tick. In other words: the max speed of the time warp."))
                                    .binding(60, Config.get("max_ticks_added")::getAsInt, value -> Config.set("max_ticks_added", value))
                                    .controller(integerOption -> new IntegerSliderController(integerOption, 1, 100, 1))
                                    .build())
                            
                            .option(Option.createBuilder(double.class)
                                    .name(Text.literal("Acceleration curve"))
                                    .tooltip(Text.literal("Scales time warp speed in relation to the amount of players sleeping."),
                                            Text.literal("Lower values will require more players to bring the warp up to full speed, and vice versa."))
                                    .binding(0.2, Config.get("acceleration_curve")::getAsDouble, value -> Config.set("acceleration_curve", value))
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
                                    .binding(false, Config.get("tick_block_entities")::getAsBoolean, value -> Config.set("tick_block_entities", value))
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
                                    .binding(false, Config.get("tick_chunks")::getAsBoolean, value -> Config.set("tick_chunks", value))
                                    .controller(BooleanController::new)
                                    .build())
                            .build())
                    .build()
                    .generateScreen(parent);
        }
    }
    
    public static class Config {
        private static Path filePath;
        private static JsonObject configJson;
        
        public static void initialize() {
            var environment = FabricLoader.getInstance().getEnvironmentType().toString().toLowerCase();
            filePath = FabricLoader.getInstance().getConfigDir()
                    .normalize()
                    .toAbsolutePath()
                    .resolveSibling("config/sleep-warp_" + environment + ".json");
            
            readJsonFile();
        }
        
        public static JsonPrimitive get(String key) {
            return configJson.get(key).getAsJsonPrimitive();
        }
    
        public static void set(String key, Number value) {
            set(key, new JsonPrimitive(value));
        }
    
        public static void set(String key, String value) {
            set(key, new JsonPrimitive(value));
        }
    
        public static void set(String key, boolean value) {
            set(key, new JsonPrimitive(value));
        }
        
        private static void set(String key, JsonPrimitive value) {
            configJson.add(key, value);
        }
        
        // Deserializes the values stored as JSON in the configuration file to an object in memory.
        // If an existing file is not found, a default configuration will be created.
        private static void readJsonFile() {
            try {
                if (Files.exists(filePath)) {
                    var jsonString = Files.readString(filePath);
                    if (!jsonString.isEmpty())
                        configJson = JsonParser.parseString(jsonString).getAsJsonObject();
                }
                
                if (configJson == null) {
                    configJson = new JsonObject();
                    
                    configJson.addProperty("max_ticks_added", 60);
                    configJson.addProperty("acceleration_curve", 0.2);
                    configJson.addProperty("tick_block_entities", false);
                    configJson.addProperty("tick_chunks", false);
                    
                    writePendingChanges();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        
        // Serializes the values currently in memory as a JSON string then writes it to the configuration file.
        private static void writePendingChanges() {
            try {
                var fileStream = Files.newOutputStream(filePath);
                var stringWriter = new StringWriter();
                var jsonWriter = new JsonWriter(stringWriter);
                
                jsonWriter.setLenient(true);
                jsonWriter.setIndent("  ");
                
                Streams.write(configJson, jsonWriter);
                fileStream.write(stringWriter.toString().getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
