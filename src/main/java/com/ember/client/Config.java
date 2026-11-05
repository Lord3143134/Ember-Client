package com.ember.client;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    public static String salt = "", codeHash = "";
    public static boolean hud = true, toggleMessages = true;

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("ember.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Config() {}

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            JsonObject o = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            salt = str(o, "salt", ""); codeHash = str(o, "codeHash", "");
            hud = !o.has("hud") || o.get("hud").getAsBoolean();
            toggleMessages = !o.has("toggleMessages") || o.get("toggleMessages").getAsBoolean();
            JsonObject mods = o.has("modules") ? o.getAsJsonObject("modules") : new JsonObject();
            for (Module m : Modules.all()) {
                if (!mods.has(m.name)) continue;
                JsonObject j = mods.getAsJsonObject(m.name);
                m.enabled = j.has("enabled") && j.get("enabled").getAsBoolean();
                m.favorite = j.has("favorite") && j.get("favorite").getAsBoolean();
                m.key = j.has("key") ? j.get("key").getAsInt() : -1;
                JsonObject st = j.has("settings") ? j.getAsJsonObject("settings") : new JsonObject();
                for (Setting s : m.settings) {
                    if (!st.has(s.name)) continue;
                    switch (s.type) {
                        case BOOL -> s.bool = st.get(s.name).getAsBoolean();
                        case NUM -> s.setNum(st.get(s.name).getAsDouble());
                        case MODE -> s.mode = Math.floorMod(st.get(s.name).getAsInt(), s.modes.length);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Ember] could not read config: " + e);
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("salt", salt);
            o.addProperty("codeHash", codeHash);
            o.addProperty("hud", hud);
            o.addProperty("toggleMessages", toggleMessages);
            JsonObject mods = new JsonObject();
            for (Module m : Modules.all()) {
                JsonObject j = new JsonObject();
                j.addProperty("enabled", m.enabled);
                j.addProperty("favorite", m.favorite);
                j.addProperty("key", m.key);
                JsonObject st = new JsonObject();
                for (Setting s : m.settings) {
                    switch (s.type) {
                        case BOOL -> st.addProperty(s.name, s.bool);
                        case NUM -> st.addProperty(s.name, s.num);
                        case MODE -> st.addProperty(s.name, s.mode);
                    }
                }
                j.add("settings", st);
                mods.add(m.name, j);
            }
            o.add("modules", mods);
            Files.writeString(FILE, GSON.toJson(o));
        } catch (Exception e) {
            System.err.println("[Ember] could not save config: " + e);
        }
    }

    private static String str(JsonObject o, String k, String d) {
        return o.has(k) ? o.get(k).getAsString() : d;
    }
}
