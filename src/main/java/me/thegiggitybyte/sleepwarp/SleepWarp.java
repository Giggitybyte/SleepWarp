package me.thegiggitybyte.sleepwarp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import dev.isxander.yacl.gui.controllers.string.number.IntegerFieldController;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class SleepWarp implements ModInitializer, ModMenuApi {
    private static Path configPath;
    private static JsonObject config;
    
    @Override
    public void onInitialize() {
        configPath = FabricLoader.getInstance().getConfigDir()
                .normalize()
                .toAbsolutePath()
                .resolveSibling("config/sleep-warp.json");
        
        readConfigJson();
    }
    
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> YetAnotherConfigLib.createBuilder()
                .title(Text.literal("SleepWarp"))
                .save(SleepWarp::writeConfigJson)
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Speed Settings"))
                        .tooltip(Text.literal("Settings which control how fast time passes"))
                        
                        .option(Option.createBuilder(int.class)
                                .name(Text.literal("Maximum ticks added"))
                                .tooltip(Text.literal("Maximum amount of ticks that can be added to the time every server tick. In other words: the max speed of the time warp."))
                                .binding(60, config.get("max_ticks_added")::getAsInt, value -> config.addProperty("max_ticks_added", value))
                                .controller(integerOption -> new IntegerSliderController(integerOption, 1, 100, 1))
                                .build())
                        
                        .option(Option.createBuilder(double.class)
                                .name(Text.literal("Acceleration curve"))
                                .tooltip(Text.literal("Scales time warp speed in relation to the amount of players sleeping."),
                                        Text.literal("Lower values will require more players to bring the warp up to full speed, and vice versa."))
                                .binding(0.2, config.get("acceleration_curve")::getAsDouble, value -> config.addProperty("acceleration_curve", value))
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
                                .binding(false, config.get("tick_block_entities")::getAsBoolean, value -> config.addProperty("tick_block_entities", value))
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
                                .binding(false, config.get("tick_chunks")::getAsBoolean, value -> config.addProperty("tick_chunks", value))
                                .controller(BooleanController::new)
                                .build())
                        .build())
                .build()
                .generateScreen(parent);
    }
    
    public static void readConfigJson() {
        try {
            if (Files.exists(configPath)) {
                var jsonString = Files.readString(configPath);
                if (!jsonString.isEmpty()) JsonParser.parseString(jsonString).getAsJsonObject();
            }
            
            if (config == null) {
                config = new JsonObject();
                
                config.addProperty("max_ticks_added", 60);
                config.addProperty("acceleration_curve", 0.2);
                config.addProperty("tick_block_entities", false);
                config.addProperty("tick_chunks", false);
                
                writeConfigJson();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void writeConfigJson() {
        try {
            var fileStream = Files.newOutputStream(configPath);
            var stringWriter = new StringWriter();
            var jsonWriter = new JsonWriter(stringWriter);
            
            jsonWriter.setLenient(true);
            jsonWriter.setIndent("  ");
            
            Streams.write(config, jsonWriter);
            fileStream.write(stringWriter.toString().getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static JsonObject getConfig() {
        return config;
    }
}
