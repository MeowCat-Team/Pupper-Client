package cn.pupperclient.management.mod.settings.impl;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.settings.Setting;

public class BooleanSetting extends Setting {

    private boolean defaultValue, enabled;

    public BooleanSetting(String name, String description, String icon, Mod parent, boolean enabled) {
        super(name, description, icon, parent);

        this.enabled = enabled;
        this.defaultValue = enabled;

        PupperClient.getInstance().getModManager().addSetting(this);
    }

    @Override
    public void reset() {
        this.enabled = defaultValue;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(boolean defaultValue) {
        this.defaultValue = defaultValue;
    }
}
