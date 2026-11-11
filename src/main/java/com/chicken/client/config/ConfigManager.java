package com.chicken.client.config;

import com.chicken.client.ChickenClient;
import com.chicken.client.core.Module;
import com.chicken.client.settings.Setting;
import com.chicken.client.settings.SettingGroup;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Stores module states and settings as config/chicken-client/config.json. */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file = FabricLoader.getInstance().getConfigDir()
        .resolve("chicken-client").resolve("config.json");

    public void save() {
        try {
            Files.createDirectories(file.getParent());

            JsonObject modules = new JsonObject();

            for (Module module : ChickenClient.modules().all()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("enabled", module.isEnabled());
                entry.addProperty("bind", module.getBind());

                JsonObject settings = new JsonObject();
                for (SettingGroup group : module.settingsGroups()) {
                    for (Setting<?> setting : group.settings()) {
                        settings.add(setting.name(), setting.save());
                    }
                }
                entry.add("settings", settings);
                modules.add(module.name(), entry);
            }

            JsonObject root = new JsonObject();
            root.add("modules", modules);

            Files.writeString(file, GSON.toJson(root));
        } catch (Exception e) {
            ChickenClient.LOG.error("Could not save config", e);
        }
    }

    public void load() {
        try {
            if (!Files.exists(file)) return;

            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (!root.has("modules")) return;

            JsonObject modules = root.getAsJsonObject("modules");

            for (Module module : ChickenClient.modules().all()) {
                if (!modules.has(module.name())) continue;

                JsonObject entry = modules.getAsJsonObject(module.name());

                if (entry.has("bind")) module.setBind(entry.get("bind").getAsInt());

                if (entry.has("settings")) {
                    JsonObject settings = entry.getAsJsonObject("settings");
                    for (SettingGroup group : module.settingsGroups()) {
                        for (Setting<?> setting : group.settings()) {
                            if (settings.has(setting.name())) setting.load(settings.get(setting.name()));
                        }
                    }
                }

                if (entry.has("enabled") && entry.get("enabled").getAsBoolean()) module.setEnabled(true);
            }

            ChickenClient.LOG.info("Config loaded from {}", file);
        } catch (Exception e) {
            ChickenClient.LOG.error("Could not load config", e);
        }
    }

    public Path file() {
        return file;
    }
}
