package cn.pupperclient.management.config.impl;

import java.io.File;
import java.util.List;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.mod.impl.hud.DynamicIsland;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.management.config.Config;
import cn.pupperclient.management.config.ConfigType;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModManager;
import cn.pupperclient.management.mod.api.AnchorPosition;
import cn.pupperclient.management.mod.api.Position;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.management.mod.settings.Setting;
import cn.pupperclient.management.mod.settings.impl.*;
import cn.pupperclient.utils.color.ColorUtils;
import cn.pupperclient.utils.misc.JsonUtils;

public class ModConfig extends Config {

    public ModConfig() {
        super(ConfigType.MOD);
    }

    @Override
    public void onLoad() {
        ModManager modManager = PupperClient.getInstance().getModManager();
        JsonObject modsJson = JsonUtils.getObjectProperty(jsonObject, "mods");

        if (modsJson == null) {
            modsJson = new JsonObject();
        }

        resetAllMods(modManager);
        loadModsConfiguration(modManager, modsJson);
    }

    @Override
    public void onSave() {
        ModManager modManager = PupperClient.getInstance().getModManager();
        JsonObject modsJson = new JsonObject();

        saveModsConfiguration(modManager, modsJson);
        jsonObject.add("mods", modsJson);
    }

    private void resetAllMods(ModManager modManager) {
        for (Mod mod : modManager.getMods()) {
            mod.setEnabled(false);
        }
    }

    private void loadModsConfiguration(ModManager modManager, JsonObject modsJson) {
        DynamicIsland.setConfigLoading(true);

        for (Mod mod : modManager.getMods()) {
            JsonObject modJson = JsonUtils.getObjectProperty(modsJson, mod.getName());

            if (modJson != null) {
                loadModBasicSettings(mod, modJson);
                loadHUDModSettings(mod, modJson);
                loadModSpecificSettings(mod, modManager, modJson);
            }
        }

        DynamicIsland.setConfigLoading(false);
    }

    private void loadModBasicSettings(Mod mod, JsonObject modJson) {
        boolean enabled = JsonUtils.getBooleanProperty(modJson, "enabled", false);
        mod.setEnabled(enabled);
    }

    private void loadHUDModSettings(Mod mod, JsonObject modJson) {
        if (mod instanceof HUDMod hudMod) {
            Position position = hudMod.getPosition();

            int anchorId = JsonUtils.getIntProperty(modJson, "anchor", 0);
            float scale = JsonUtils.getFloatProperty(modJson, "scale", 1.0f);
            float x = JsonUtils.getFloatProperty(modJson, "x", 0.0f);
            float y = JsonUtils.getFloatProperty(modJson, "y", 0.0f);

            position.setAnchor(AnchorPosition.get(anchorId));
            position.setScale(scale);
            position.setRawPosition(x, y);
        }
    }

    private void loadModSpecificSettings(Mod mod, ModManager modManager, JsonObject modJson) {
        List<Setting> settings = modManager.getSettingsByMod(mod);

        if (settings.isEmpty()) {
            return;
        }

        JsonObject settingsJson = JsonUtils.getObjectProperty(modJson, "settings");
        if (settingsJson == null) {
            return;
        }

        for (Setting setting : settings) {
            loadSettingValue(setting, settingsJson);
        }
    }

    private void loadSettingValue(Setting setting, JsonObject settingsJson) {
        if (setting instanceof BooleanSetting) {
            loadBooleanSetting((BooleanSetting) setting, settingsJson);
        } else if (setting instanceof ColorSetting) {
            loadColorSetting((ColorSetting) setting, settingsJson);
        } else if (setting instanceof ComboSetting) {
            loadComboSetting((ComboSetting) setting, settingsJson);
        } else if (setting instanceof HctColorSetting) {
            loadHctColorSetting((HctColorSetting) setting, settingsJson);
        } else if (setting instanceof KeybindSetting) {
            loadKeybindSetting((KeybindSetting) setting, settingsJson);
        } else if (setting instanceof NumberSetting) {
            loadNumberSetting((NumberSetting) setting, settingsJson);
        } else if (setting instanceof StringSetting) {
            loadStringSetting((StringSetting) setting, settingsJson);
        } else if (setting instanceof FileSetting) {
            loadFileSetting((FileSetting) setting, settingsJson);
        }
    }

    private void loadBooleanSetting(BooleanSetting setting, JsonObject settingsJson) {
        boolean value = JsonUtils.getBooleanProperty(settingsJson, setting.getName(), setting.getDefaultValue());
        setting.setEnabled(value);
    }

    private void loadColorSetting(ColorSetting setting, JsonObject settingsJson) {
        int colorValue = JsonUtils.getIntProperty(settingsJson, setting.getName(), setting.getColor().getRGB());
        setting.setColor(ColorUtils.getColorFromInt(colorValue));
    }

    private void loadComboSetting(ComboSetting setting, JsonObject settingsJson) {
        String option = JsonUtils.getStringProperty(settingsJson, setting.getName(), "");
        String finalOption = setting.has(option) ? option : setting.getDefaultOption();
        setting.setOption(finalOption);
    }

    private void loadHctColorSetting(HctColorSetting setting, JsonObject settingsJson) {
        int hctValue = JsonUtils.getIntProperty(settingsJson, setting.getName(), setting.getDefaultHct().toInt());
        setting.setHct(Hct.fromInt(hctValue));
    }

    private void loadKeybindSetting(KeybindSetting setting, JsonObject settingsJson) {
        JsonObject keybindJson = JsonUtils.getObjectProperty(settingsJson, setting.getName());

        if (keybindJson == null) {
            setting.setKey(setting.getDefaultKey());
            return;
        }

        String inputType = JsonUtils.getStringProperty(keybindJson, "type", "");
        int keyCode = JsonUtils.getIntProperty(keybindJson, "code", -1);

        InputConstants.Type type = determineInputType(inputType);

        if (!inputType.isEmpty() && keyCode != -1) {
            setting.setKey(type.getOrCreate(keyCode));
        } else {
            setting.setKey(setting.getDefaultKey());
        }
    }

    private void loadNumberSetting(NumberSetting setting, JsonObject settingsJson) {
        float value = JsonUtils.getFloatProperty(settingsJson, setting.getName(), setting.getValue());
        setting.setValue(value);
    }

    private void loadStringSetting(StringSetting setting, JsonObject settingsJson) {
        String value = JsonUtils.getStringProperty(settingsJson, setting.getName(), setting.getDefaultValue());
        setting.setValue(value);
    }

    private void loadFileSetting(FileSetting setting, JsonObject settingsJson) {
        String filePath = JsonUtils.getStringProperty(settingsJson, setting.getName(), "");
        File file = (filePath != null && !filePath.isEmpty()) ? new File(filePath) : setting.getDefaultValue();
        setting.setFile(file);
    }

    private void saveModsConfiguration(ModManager modManager, JsonObject modsJson) {
        for (Mod mod : modManager.getMods()) {
            JsonObject modJson = new JsonObject();
            saveModBasicSettings(mod, modJson);
            saveHUDModSettings(mod, modJson);
            saveModSpecificSettings(mod, modManager, modJson);
            modsJson.add(mod.getName(), modJson);
        }
    }

    private void saveModBasicSettings(Mod mod, JsonObject modJson) {
        modJson.addProperty("enabled", mod.isEnabled());
    }

    private void saveHUDModSettings(Mod mod, JsonObject modJson) {
        if (mod instanceof HUDMod hudMod) {
            Position position = hudMod.getPosition();

            modJson.addProperty("x", position.getRawX());
            modJson.addProperty("y", position.getRawY());
            modJson.addProperty("scale", position.getScale());
            modJson.addProperty("anchor", position.getAnchor().getId());
        }
    }

    private void saveModSpecificSettings(Mod mod, ModManager modManager, JsonObject modJson) {
        List<Setting> settings = modManager.getSettingsByMod(mod);

        if (settings.isEmpty()) {
            return;
        }

        JsonObject settingsJson = new JsonObject();

        for (Setting setting : settings) {
            saveSettingValue(setting, settingsJson);
        }

        modJson.add("settings", settingsJson);
    }

    private void saveSettingValue(Setting setting, JsonObject settingsJson) {
        if (setting instanceof BooleanSetting) {
            saveBooleanSetting((BooleanSetting) setting, settingsJson);
        } else if (setting instanceof ColorSetting) {
            saveColorSetting((ColorSetting) setting, settingsJson);
        } else if (setting instanceof ComboSetting) {
            saveComboSetting((ComboSetting) setting, settingsJson);
        } else if (setting instanceof HctColorSetting) {
            saveHctColorSetting((HctColorSetting) setting, settingsJson);
        } else if (setting instanceof KeybindSetting) {
            saveKeybindSetting((KeybindSetting) setting, settingsJson);
        } else if (setting instanceof NumberSetting) {
            saveNumberSetting((NumberSetting) setting, settingsJson);
        } else if (setting instanceof StringSetting) {
            saveStringSetting((StringSetting) setting, settingsJson);
        } else if (setting instanceof FileSetting) {
            saveFileSetting((FileSetting) setting, settingsJson);
        }
    }

    private void saveBooleanSetting(BooleanSetting setting, JsonObject settingsJson) {
        settingsJson.addProperty(setting.getName(), setting.isEnabled());
    }

    private void saveColorSetting(ColorSetting setting, JsonObject settingsJson) {
        settingsJson.addProperty(setting.getName(), setting.getColor().getRGB());
    }

    private void saveComboSetting(ComboSetting setting, JsonObject settingsJson) {
        settingsJson.addProperty(setting.getName(), setting.getOption());
    }

    private void saveHctColorSetting(HctColorSetting setting, JsonObject settingsJson) {
        settingsJson.addProperty(setting.getName(), setting.getHct().toInt());
    }

    private void saveKeybindSetting(KeybindSetting setting, JsonObject settingsJson) {
        JsonObject keybindJson = new JsonObject();
        InputConstants.Type type = setting.getKey().getType();

        String saveType = getInputTypeString(type);
        keybindJson.addProperty("type", saveType);
        keybindJson.addProperty("code", setting.getKey().getValue());

        settingsJson.add(setting.getName(), keybindJson);
    }

    private void saveNumberSetting(NumberSetting setting, JsonObject settingsJson) {
        settingsJson.addProperty(setting.getName(), setting.getValue());
    }

    private void saveStringSetting(StringSetting setting, JsonObject settingsJson) {
        settingsJson.addProperty(setting.getName(), setting.getValue());
    }

    private void saveFileSetting(FileSetting setting, JsonObject settingsJson) {
        File file = setting.getFile();
        String filePath = (file != null) ? file.getAbsolutePath() : "null";
        settingsJson.addProperty(setting.getName(), filePath);
    }

    private InputConstants.Type determineInputType(String inputType) {
        return switch (inputType) {
            case "key" -> InputConstants.Type.KEYSYM;
            case "mouse" -> InputConstants.Type.MOUSE;
            default -> InputConstants.Type.SCANCODE;
        };
    }

    private String getInputTypeString(InputConstants.Type type) {
        if (type.equals(InputConstants.Type.KEYSYM)) {
            return "key";
        } else if (type.equals(InputConstants.Type.MOUSE)) {
            return "mouse";
        } else {
            return "scancode";
        }
    }
}
