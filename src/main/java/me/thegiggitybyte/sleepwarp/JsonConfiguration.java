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
    private static final JsonObject DEFAULT_JSON;
    private JsonObject userJson;
    private Path filePath;
    
    static {
        DEFAULT_JSON = new JsonObject();
        
        DEFAULT_JSON.addProperty("max_ticks_added", 60);
        DEFAULT_JSON.addProperty("player_scale", 0.6);
        DEFAULT_JSON.addProperty("action_bar_messages", true);
        DEFAULT_JSON.addProperty("use_sleep_percentage", false);
        DEFAULT_JSON.addProperty("tick_block_entities", false);
        DEFAULT_JSON.addProperty("tick_chunks", false);
        DEFAULT_JSON.addProperty("performance_mode", false);
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
                    userJson = JsonParser.parseString(jsonString).getAsJsonObject();
                
            } else {
                userJson = new JsonObject();
            }
            
            validateJsonStructure();
            writePendingChanges();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    static JsonObject getDefaultJson() {
        return DEFAULT_JSON;
    }
    
    JsonObject getInstance() {
        return userJson;
    }
    
    public boolean has(String key) {
        return userJson.has(key);
    }
    
    public JsonPrimitive get(String key) {
        var jsonValue = userJson.get(key);
        
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
        userJson.add(key, value);
    }
    
    public void writePendingChanges() {
        try (var fileStream = Files.newOutputStream(filePath)) {
            var stringWriter = new StringWriter();
            var jsonWriter = new JsonWriter(stringWriter);
            
            jsonWriter.setLenient(true);
            jsonWriter.setIndent("  ");
            
            validateJsonStructure();
            
            Streams.write(userJson, jsonWriter);
            fileStream.write(stringWriter.toString().getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private void validateJsonStructure() {
        for (var entry : DEFAULT_JSON.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            
            if (!userJson.has(key) || !userJson.get(key).isJsonPrimitive()) {
                userJson.add(key, value);
            }
        }
    }
}