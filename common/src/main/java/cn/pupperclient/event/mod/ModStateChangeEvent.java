package cn.pupperclient.event.mod;

import cn.pupperclient.PupperClient;
import cn.pupperclient.event.Event;
import cn.pupperclient.management.mod.Mod;

public class ModStateChangeEvent extends Event {
    private final Mod mod;
    String modName;
    private final boolean enabled;
    private final long timestamp;

    public ModStateChangeEvent(Mod mod, boolean enabled) {
        this.mod = mod;
        this.enabled = enabled;
        this.timestamp = System.currentTimeMillis();
    }

    public ModStateChangeEvent(String modName, boolean enabled) {
        this.modName = modName;
        this.enabled = enabled;
        this.timestamp = System.currentTimeMillis();
        this.mod = PupperClient.getInstance().getModManager().getModByName(modName);
    }

    public Mod getMod() {
        return mod;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getModName() {
        return mod.getName();
    }
}
