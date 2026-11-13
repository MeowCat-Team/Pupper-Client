/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cn.pupperclient.management.config;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import cn.pupperclient.PupperClient;
import cn.pupperclient.management.config.impl.KeyConfig;
import cn.pupperclient.management.config.impl.ModConfig;
import cn.pupperclient.management.keybind.KeybindManager;

public class ConfigManager {

    private final List<Config> configs = new ArrayList<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public ConfigManager() {
        configs.add(new ModConfig());
        load(ConfigType.MOD);
        configs.add(new KeyConfig());
        load(ConfigType.KEY);
    }

    public void save(ConfigType type) {
        Config config = getConfig(type);
        if (config == null || config.getFile() == null) {
            return;
        }

        Path targetPath = config.getFile().toPath();
        Path tempPath = Path.of(targetPath.toAbsolutePath() + ".tmp");
        Path backupPath = Path.of(targetPath.toAbsolutePath() + ".bak");

        try {
            Path parent = targetPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(tempPath)) {
                config.onSave();
                GSON.toJson(config.getJsonObject(), writer);
            }
        } catch (IOException e) {
            PupperClient.LOGGER.error("Failed to write temp config: {}", type, e);
            return;
        }

        if (Files.exists(targetPath)) {
            try {
                Files.copy(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                PupperClient.LOGGER.warn("Failed to backup config: {}", type, e);
            }
        }

        boolean moved = false;
        boolean atomic = true;
        int maxRetries = 3;
        long retryDelayMs = 100L;

        for (int i = 0; i < maxRetries && !moved; i++) {
            try {
                if (atomic) {
                    Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } else {
                    Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                moved = true;
            } catch (AtomicMoveNotSupportedException e) {
                atomic = false;
                PupperClient.LOGGER.warn("Atomic move not supported, falling back for: {}", type);
            } catch (IOException e) {
                PupperClient.LOGGER.warn("Attempt {} to move config failed for {}: {}", i + 1, type, e.getMessage());
                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        if (!moved && atomic) {
            try {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                moved = true;
            } catch (IOException e) {
                PupperClient.LOGGER.error("Fallback move failed: {}", type, e);
            }
        }

        if (!moved && Files.exists(tempPath)) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException e) {
                PupperClient.LOGGER.warn("Failed to delete temp file: {}", tempPath, e);
            }
        }

        if (!moved) {
            PupperClient.LOGGER.error("Failed to save config after all attempts: {}", type);
        }
    }

    public void load(ConfigType type) {
        Config config = getConfig(type);
        if (config == null) {
            return;
        }

        if (config.getFile() == null) {
            config.setJsonObject(new JsonObject());
            return;
        }

        File file = config.getFile();
        if (!file.exists()) {
            File backup = new File(file.getAbsolutePath() + ".bak");
            if (backup.exists()) {
                PupperClient.LOGGER.warn("Config file {} missing, using backup", type);
                file = backup;
            } else {
                config.setJsonObject(new JsonObject());
                return;
            }
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject loadedJson = GSON.fromJson(reader, JsonObject.class);
            config.setJsonObject(loadedJson != null ? loadedJson : new JsonObject());
            config.onLoad();

            if (type == ConfigType.KEY) {
                KeybindManager.getInstance().refreshKeybinds();
            }
        } catch (Exception e) {
            PupperClient.LOGGER.error("Failed to load config: {}, trying backup", config.getType(), e);

            // Try backup if main file fails
            File backup = new File(config.getFile().getAbsolutePath() + ".bak");
            if (backup.exists() && !file.equals(backup)) {
                try (FileReader reader = new FileReader(backup)) {
                    JsonObject loadedJson = GSON.fromJson(reader, JsonObject.class);
                    config.setJsonObject(loadedJson != null ? loadedJson : new JsonObject());
                    config.onLoad();
                } catch (Exception ex) {
                    PupperClient.LOGGER.error("Backup also failed for config: {}", type, ex);
                    config.setJsonObject(new JsonObject());
                }
            } else {
                config.setJsonObject(new JsonObject());
            }
        }
    }

    /** @return the config for {@code type}, or {@code null} when that type is not registered */
    public Config getConfig(ConfigType type) {
        for (Config config : configs) {
            if (config.getType() == type) {
                return config;
            }
        }
        return null;
    }
}
