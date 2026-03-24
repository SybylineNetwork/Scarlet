package net.sybyline.scarlet.util;

import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import io.github.vrchatapi.JSON;

public interface EventSchemas
{

    public static interface EventProps
    {
        public JsonObject object();
        
        public default boolean hasNullable(String key) { return this.object().has(key); }
        public default boolean has(String key) { return this.hasNullable(key) && !this.get(key).isJsonNull(); }
        public default JsonElement get(String key) { return this.object().get(key); }
        
        public default Boolean bool(String key) { return this.element(key, Boolean.class); }
        public default boolean bool(String key, boolean def) { return Optional.ofNullable(this.bool(key)).orElse(def); }
        
        public default Integer integer(String key) { return this.element(key, Integer.class); }
        public default int integer(String key, int def) { return Optional.ofNullable(this.integer(key)).orElse(def); }
        
        public default Double decimal(String key) { return this.element(key, Double.class); }
        public default double decimal(String key, double def) { return Optional.ofNullable(this.decimal(key)).orElse(def); }
        
        public default String string(String key) { return this.element(key, String.class); }
        public default String string(String key, String def) { return Optional.ofNullable(this.string(key)).orElse(def); }
        
        public default String[] stringArr(String key) { return this.element(key, String[].class); }
        public default String[] stringArr(String key, String... def) { return Optional.ofNullable(this.stringArr(key)).orElse(def); }
        
        public default <T> T component(Class<T>               type) { return JSON.getGson().fromJson(this.object(), type); }
        public default <T> T component(TypeToken<T>           type) { return JSON.getGson().fromJson(this.object(), type); }
        public default <T> T component(java.lang.reflect.Type type) { return JSON.getGson().fromJson(this.object(), type); }
        
        public default <T> T element(String key, Class<T>               type) { return JSON.getGson().fromJson(this.get(key), type); }
        public default <T> T element(String key, TypeToken<T>           type) { return JSON.getGson().fromJson(this.get(key), type); }
        public default <T> T element(String key, java.lang.reflect.Type type) { return JSON.getGson().fromJson(this.get(key), type); }
    }

    // Amplitude cache

    public static class AmplitudeCache implements EventProps
    {
        public String app_version;
        public String device_id;
        public String device_model;
        public String device_name;
        public int event_id;
        public JsonElement event_properties;
        public String event_type;
        public int insert_id;
        public String ip;
        public String language;
        public String os_name;
        public String os_version;
        public String platform;
        public long session_id;
        public long time;
        public String user_id;
        public JsonElement user_properties;
        @Override public JsonObject object() { return this.event_properties != null && this.event_properties.isJsonObject() ? this.event_properties.getAsJsonObject() : new JsonObject(); }
    }

}
