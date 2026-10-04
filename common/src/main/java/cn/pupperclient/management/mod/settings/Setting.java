package cn.pupperclient.management.mod.settings;

import cn.pupperclient.management.mod.Mod;

public abstract class Setting {

    private String name, description, icon;
    private Mod parent;

    public Setting(String name, String description, String icon, Mod parent) {
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.parent = parent;
    }

    public abstract void reset();

    public boolean isVisible() {
        return true;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public Mod getParent() {
        return parent;
    }
}
