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

package cn.pupperclient.management.config.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.mod.impl.hud.DynamicIsland;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.management.config.Config;
import cn.pupperclient.management.config.ConfigType;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModManager;
import cn.pupperclient.management.mod.ModNames;
import cn.pupperclient.management.mod.api.AnchorPosition;
import cn.pupperclient.management.mod.api.Position;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.management.mod.setting.Setting;
import cn.pupperclient.management.mod.setting.*;
import cn.pupperclient.utils.color.ColorUtils;
import cn.pupperclient.utils.misc.JsonUtils;

public class ModConfig extends Config {

    /** Serialises one setting subtype to and from the shared settings object. */
    private interface SettingCodec {
        void load(Setting setting, JsonObject settings);
        void save(Setting setting, JsonObject settings);
    }

    /**
     * One codec per setting subtype. A subtype with no entry keeps its default value,
     * which is why {@link #codec} has no fallback branch.
     */
    private static final Map<Class<? extends Setting>, SettingCodec> CODECS = new LinkedHashMap<>();

    static {
        CODECS.put(BooleanSetting.class, new SettingCodec() {
            @Override public void load(Setting setting, JsonObject settings) {
                BooleanSetting value = (BooleanSetting) setting;
                value.setEnabled(JsonUtils.getBooleanProperty(settings, value.getName(), value.getDefaultValue()));
            }
            @Override public void save(Setting setting, JsonObject settings) {
                settings.addProperty(setting.getName(), ((BooleanSetting) setting).isEnabled());
            }
        });
        CODECS.put(ColorSetting.class, new SettingCodec() {
            @Override public void load(Setting setting, JsonObject settings) {
                ColorSetting value = (ColorSetting) setting;
                int stored = JsonUtils.getIntProperty(settings, value.getName(), value.getColor().getRGB());
                value.setColor(ColorUtils.getColorFromInt(stored));
            }
            @Override public void save(Setting setting, JsonObject settings) {
                settings.addProperty(setting.getName(), ((ColorSetting) setting).getColor().getRGB());
            }
        });
        CODECS.put(ComboSetting.class, new SettingCodec() {
            @Override public void load(Setting setting, JsonObject settings) {
                ComboSetting value = (ComboSetting) setting;
                String stored = JsonUtils.getStringProperty(settings, value.getName(), "");
                value.setOption(value.has(stored) ? stored : value.getDefaultOption());
            }
            @Override public void save(Setting setting, JsonObject settings) {
                settings.addProperty(setting.getName(), ((ComboSetting) setting).getOption());
            }
        });
        CODECS.put(HctColorSetting.class, new SettingCodec() {
            @Override public void load(Setting setting, JsonObject settings) {
                HctColorSetting value = (HctColorSetting) setting;
                int stored = JsonUtils.getIntProperty(settings, value.getName(), value.getDefaultHct().toInt());
                value.setHct(Hct.fromInt(stored));
            }
            @Override public void save(Setting setting, JsonObject settings) {
                settings.addProperty(setting.getName(), ((HctColorSetting) setting).getHct().toInt());
            }
        });
        CODECS.put(KeybindSetting.class, new SettingCodec() {
            @Override public void load(Setting setting, JsonObject settings) {
                KeybindSetting value = (KeybindSetting) setting;
                JsonObject stored = JsonUtils.getObjectProperty(settings, value.getName());
                if (stored == null) {
                    value.setKey(value.getDefaultKey());
                    return;
                }
                String inputType = JsonUtils.getStringProperty(stored, "type", "");
                int keyCode = JsonUtils.getIntProperty(stored, "code", -1);
                if (!inputType.isEmpty() && keyCode != -1) {
                    value.setKey(determineInputType(inputType).getOrCreate(keyCode));
                } else {
                    value.setKey(value.getDefaultKey());
                }
            }
            @Override public void save(Setting setting, JsonObject settings) {
                KeybindSetting value = (KeybindSetting) setting;
                JsonObject keybind = new JsonObject();
                keybind.addProperty("type", getInputTypeString(value.getKey().getType()));
                keybind.addProperty("code", value.getKey().getValue());
                settings.add(value.getName(), keybind);
            }
        });
        CODECS.put(NumberSetting.class, new SettingCodec() {
            @Override public void load(Setting setting, JsonObject settings) {
                NumberSetting value = (NumberSetting) setting;
                value.setValue(JsonUtils.getFloatProperty(settings, value.getName(), value.getValue()));
            }
            @Override public void save(Setting setting, JsonObject settings) {
                settings.addProperty(setting.getName(), ((NumberSetting) setting).getValue());
            }
        });
        CODECS.put(StringSetting.class, new SettingCodec() {
            @Override public void load(Setting setting, JsonObject settings) {
                StringSetting value = (StringSetting) setting;
                value.setValue(JsonUtils.getStringProperty(settings, value.getName(), value.getDefaultValue()));
            }
            @Override public void save(Setting setting, JsonObject settings) {
                settings.addProperty(setting.getName(), ((StringSetting) setting).getValue());
            }
        });
    }

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

        List<String> migratedKeys = new ArrayList<>();
        List<Mod> mods = modManager.getMods();

        for (Mod mod : mods) {
            JsonObject modJson = ModNames.find(modsJson, mod, mods, migratedKeys);

            if (modJson != null) {
                loadModBasicSettings(mod, modJson);
                loadHUDModSettings(mod, modJson);
                loadModSpecificSettings(mod, modManager, modJson);
            }
        }

        if (!migratedKeys.isEmpty()) {
            // The next save rewrites every entry under its stable key, dropping these.
            PupperClient.LOGGER.info("Migrated {} mod settings from translated names to stable keys", migratedKeys.size());
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
            codec(setting).load(setting, settingsJson);
        }
    }

    private void saveModsConfiguration(ModManager modManager, JsonObject modsJson) {
        for (Mod mod : modManager.getMods()) {
            JsonObject modJson = new JsonObject();
            saveModBasicSettings(mod, modJson);
            saveHUDModSettings(mod, modJson);
            saveModSpecificSettings(mod, modManager, modJson);
            modsJson.add(ModNames.configKey(mod), modJson);
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
            codec(setting).save(setting, settingsJson);
        }

        modJson.add("settings", settingsJson);
    }

    /**
     * Resolves the codec for a setting's subtype.
     *
     * @throws IllegalStateException when a subtype is added without a codec, so a new
     *         setting cannot be silently dropped from the saved configuration
     */
    private static SettingCodec codec(Setting setting) {
        for (Map.Entry<Class<? extends Setting>, SettingCodec> entry : CODECS.entrySet()) {
            if (entry.getKey().isInstance(setting)) {
                return entry.getValue();
            }
        }
        throw new IllegalStateException("No config codec for " + setting.getClass().getName());
    }

    private static InputConstants.Type determineInputType(String inputType) {
        return switch (inputType) {
            case "key" -> InputConstants.Type.KEYSYM;
            case "mouse" -> InputConstants.Type.MOUSE;
            default -> InputConstants.Type.SCANCODE;
        };
    }

    private static String getInputTypeString(InputConstants.Type type) {
        if (type.equals(InputConstants.Type.KEYSYM)) {
            return "key";
        } else if (type.equals(InputConstants.Type.MOUSE)) {
            return "mouse";
        } else {
            return "scancode";
        }
    }
}
