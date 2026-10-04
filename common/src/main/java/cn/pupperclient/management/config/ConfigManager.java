package cn.pupperclient.management.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
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
import cn.pupperclient.utils.file.FileUtils;

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

        File targetFile = config.getFile();
        File tempFile = new File(targetFile.getAbsolutePath() + ".tmp");
        File backupFile = new File(targetFile.getAbsolutePath() + ".bak");

        FileUtils.createFile(tempFile);

        if (config.getJsonObject() == null) {
            config.setJsonObject(new JsonObject());
        }

        try (FileWriter writer = new FileWriter(tempFile)) {
            config.onSave();
            GSON.toJson(config.getJsonObject(), writer);
            writer.flush();
            writer.close(); // Close before moving

            // Atomic move/replace
            if (targetFile.exists()) {
                Files.copy(targetFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            PupperClient.LOGGER.error("Failed to save config: {}", config.getType(), e);
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public void load(ConfigType type) {
        Config config = getConfig(type);

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

	public Config getConfig(ConfigType type) {
		return configs.stream().filter(config -> config.getType().equals(type)).findFirst().get();
	}
}
