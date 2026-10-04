package cn.pupperclient.management.mod;

import cn.pupperclient.PupperClient;
import cn.pupperclient.event.EventBus;

import cn.pupperclient.event.mod.ModStateChangeEvent;
import cn.pupperclient.management.keybind.KeybindManager;
import cn.pupperclient.utils.minecraft.interfaces.IMinecraft;
import cn.pupperclient.utils.language.I18n;

public class Mod implements IMinecraft {
    private final String name, description, icon;
    private boolean enabled, movable, hidden;
    private ModCategory category;
    private int key;

    public Mod(String name, String description, String icon, ModCategory category) {
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.movable = false;
        this.hidden = false;
        this.category = category;
        this.key = 0;
    }

    public Mod(String name, String description, String icon, ModCategory category, int key) {
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.key = key;
        this.movable = false;
        this.hidden = false;
        this.category = category;

        if (key != 0) {
            KeybindManager.getInstance().registerKeybind(key, this);
        }
    }

    public void setKeybind(int newKey) {
        KeybindManager.getInstance().updateKeybind(this.key, newKey, this);

        int previousKey = this.key;
        this.key = newKey;

        PupperClient.LOGGER.debug("Keybind updated for mod {}: {} -> {}", name, previousKey, newKey);
    }

    public int getKeybind() {
        return key;
    }

    public void toggle() {
        enabled = !enabled;
        notifyStateChange();

        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            notifyStateChange();

            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
        }
    }

    private void notifyStateChange() {
        EventBus.getInstance().post(new ModStateChangeEvent(this, enabled));
    }

    public void onEnable() {
        EventBus.getInstance().register(this);
        //PupperClient.LOGGER.info("Mod {} enabled", I18n.get("name"));
    }

    public void onDisable() {
        EventBus.getInstance().unregister(this);
        //PupperClient.LOGGER.info("Mod {} disabled", getName());
    }

    public void cleanup() {
        if (key != 0) {
            KeybindManager.getInstance().unregisterKeybind(key, this);
        }
    }

    public String getName() {
        return I18n.get(name);
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public boolean isMovable() {
        return movable;
    }

    public void setMovable(boolean movable) {
        this.movable = movable;
    }

    public ModCategory getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public String getRawName() {
        return name;
    }
}
