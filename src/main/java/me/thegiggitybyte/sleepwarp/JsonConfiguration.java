package me.thegiggitybyte.sleepwarp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonConfiguration {
    private static final JsonObject DEFAULT_CONFIGURATION;
    private JsonObject userConfiguration;
    private Path filePath;
    
    static {
        DEFAULT_CONFIGURATION = new JsonObject();
        
        DEFAULT_CONFIGURATION.addProperty("max_ticks_added", 60);
        DEFAULT_CONFIGURATION.addProperty("player_scale", 0.2);
        DEFAULT_CONFIGURATION.addProperty("performance_mode", false);
        DEFAULT_CONFIGURATION.addProperty("tick_block_entities", false);
        DEFAULT_CONFIGURATION.addProperty("tick_chunks", false);
    }
    
    public JsonConfiguration() {
        var environment = FabricLoader.getInstance()
                .getEnvironmentType()
                .toString()
                .toLowerCase();
        
        filePath = FabricLoader.getInstance().getConfigDir()
                .normalize()
                .toAbsolutePath()
                .resolveSibling("config/sleep-warp_" + environment + ".json");
        
        try {
            if (Files.exists(filePath)) {
                var jsonString = Files.readString(filePath);
                if (!jsonString.isEmpty())
                    userConfiguration = JsonParser.parseString(jsonString).getAsJsonObject();
                
            } else {
                userConfiguration = new JsonObject();
            }
            
            validateJsonStructure();
            writePendingChanges();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public JsonPrimitive get(String key) {
        var jsonValue = userConfiguration.get(key);
        
        if (jsonValue != null)
            return jsonValue.getAsJsonPrimitive();
        else
            throw new AssertionError("Key does not exist");
    }
    
    public void set(String key, Number value) {
        set(key, new JsonPrimitive(value));
    }
    
    public void set(String key, String value) {
        set(key, new JsonPrimitive(value));
    }
    
    public void set(String key, boolean value) {
        set(key, new JsonPrimitive(value));
    }
    
    private void set(String key, JsonPrimitive value) {
        userConfiguration.add(key, value);
    }
    
    public void writePendingChanges() {
        try {
            var fileStream = Files.newOutputStream(filePath);
            var stringWriter = new StringWriter();
            var jsonWriter = new JsonWriter(stringWriter);
            
            jsonWriter.setLenient(true);
            jsonWriter.setIndent("  ");
            
            validateJsonStructure();
            
            Streams.write(userConfiguration, jsonWriter);
            fileStream.write(stringWriter.toString().getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private void validateJsonStructure() {
        for (var entry : DEFAULT_CONFIGURATION.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            
            if (!userConfiguration.has(key) || !userConfiguration.get(key).isJsonPrimitive()) {
                userConfiguration.add(key, value);
            }
        }
    }
}