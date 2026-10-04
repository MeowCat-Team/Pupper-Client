package cn.pupperclient.management.config.impl;

import cn.pupperclient.PupperClient;
import com.google.gson.JsonObject;
import cn.pupperclient.management.config.Config;
import cn.pupperclient.management.config.ConfigType;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModManager;

public class KeyConfig extends Config {

    public KeyConfig() {
        super(ConfigType.KEY);
    }

    @Override
    public void onLoad() {
        if (jsonObject == null) {
            jsonObject = new JsonObject();
            PupperClient.LOGGER.info("Created new key config");
            return;
        }

        ModManager modManager = PupperClient.getInstance().getModManager();
        if (modManager == null) {
            PupperClient.LOGGER.warn("ModManager is not initialized, cannot load keybinds");
            return;
        }

        try {
            int loadedCount = 0;

            if (jsonObject.has("modKeybinds")) {
                JsonObject modKeybinds = jsonObject.getAsJsonObject("modKeybinds");

                for (java.util.Map.Entry<String, ?> entry : modKeybinds.entrySet()) {
                    String modName = entry.getKey();

                    try {
                        if (modKeybinds.has(modName) && modKeybinds.get(modName).isJsonPrimitive()) {
                            int keyCode = modKeybinds.get(modName).getAsInt();

                            Mod mod = modManager.getModByName(modName);
                            if (mod != null) {
                                mod.setKeybind(keyCode);
                                loadedCount++;
                                PupperClient.LOGGER.debug("Loaded keybind for mod {}: keycode {}", modName, keyCode);
                            } else {
                                PupperClient.LOGGER.warn("Mod not found for keybind: {}", modName);
                            }
                        }
                    } catch (Exception e) {
                        PupperClient.LOGGER.error("Error loading keybind for mod {}: {}", modName, e.getMessage());
                    }
                }
            }

            PupperClient.LOGGER.info("Successfully loaded {} mod keybinds", loadedCount);

        } catch (Exception e) {
            PupperClient.LOGGER.error("Failed to load keybinds from config", e);
            jsonObject = new JsonObject();
        }
    }

    @Override
    public void onSave() {
        ModManager modManager = PupperClient.getInstance().getModManager();
        if (modManager == null) {
            PupperClient.LOGGER.warn("ModManager is not initialized, cannot save keybinds");
            return;
        }

        try {
            JsonObject modKeybinds = new JsonObject();
            int savedCount = 0;

            for (Mod mod : modManager.getMods()) {
                try {
                    if (mod.getKeybind() != 0) {
                        modKeybinds.addProperty(mod.getName(), mod.getKeybind());
                        savedCount++;
                    }
                } catch (Exception e) {
                    PupperClient.LOGGER.error("Error saving keybind for mod {}: {}", mod.getName(), e.getMessage());
                }
            }

            jsonObject = new JsonObject();

            if (!modKeybinds.isEmpty()) {
                jsonObject.add("modKeybinds", modKeybinds);
            }

            jsonObject.addProperty("configVersion", "1.1");
            jsonObject.addProperty("lastSaved", System.currentTimeMillis());
            jsonObject.addProperty("totalModKeybinds", savedCount);

            PupperClient.LOGGER.info("Successfully saved {} mod keybinds", savedCount);

        } catch (Exception e) {
            PupperClient.LOGGER.error("Failed to save keybinds to config", e);
        }
    }

    public void resetAllKeybinds() {
        ModManager modManager = PupperClient.getInstance().getModManager();
        if (modManager != null) {
            int resetCount = 0;
            for (Mod mod : modManager.getMods()) {
                if (mod.getKeybind() != 0) {
                    mod.setKeybind(0);
                    resetCount++;
                }
            }
            PupperClient.LOGGER.info("Reset {} keybinds", resetCount);
        }
    }

    public String exportKeybindsAsText() {
        ModManager modManager = PupperClient.getInstance().getModManager();
        if (modManager == null) return "ModManager not available";

        StringBuilder sb = new StringBuilder();
        sb.append("=== Pupper Client Keybind Configuration ===\n");
        sb.append("Generated: ").append(new java.util.Date()).append("\n\n");

        // 模组按键
        sb.append("MOD KEYBINDS:\n");
        sb.append("-------------\n");

        long modsWithKeybinds = modManager.getMods().stream()
            .filter(mod -> mod.getKeybind() != 0)
            .sorted((m1, m2) -> m1.getName().compareToIgnoreCase(m2.getName()))
            .peek(mod -> {
                String keyName = getKeyName(mod.getKeybind());
                sb.append(String.format("%-30s: %s (Keycode: %d)\n",
                    mod.getName(), keyName, mod.getKeybind()));
            })
            .count();

        if (modsWithKeybinds == 0) {
            sb.append("No keybinds configured\n");
        }

        return sb.toString();
    }

    // 将键码转换为可读的键名
    private String getKeyName(int keyCode) {
        if (keyCode == 0) return "NONE";

        // 常见按键映射
        switch (keyCode) {
            case 32: return "SPACE";
            case 256: return "ESCAPE";
            case 257: return "ENTER";
            case 258: return "TAB";
            case 259: return "BACKSPACE";
            case 260: return "INSERT";
            case 261: return "DELETE";
            case 262: return "RIGHT";
            case 263: return "LEFT";
            case 264: return "DOWN";
            case 265: return "UP";
            case 340: return "LEFT_SHIFT";
            case 341: return "LEFT_CONTROL";
            case 342: return "LEFT_ALT";
            case 344: return "RIGHT_SHIFT";
            case 345: return "RIGHT_CONTROL";
            case 346: return "RIGHT_ALT";
            default:
                if (keyCode >= 65 && keyCode <= 90) {
                    return Character.toString((char) keyCode);
                } else if (keyCode >= 48 && keyCode <= 57) {
                    return "NUM_" + (keyCode - 48);
                } else if (keyCode >= 290 && keyCode <= 301) {
                    return "F" + (keyCode - 289);
                }
                return "KEY_" + keyCode;
        }
    }
}
