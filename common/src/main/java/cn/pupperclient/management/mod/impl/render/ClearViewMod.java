package cn.pupperclient.management.mod.impl.render;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.skia.font.Icon;

/**
 * Removes the screen effects of blindness, darkness and nausea on the local player.
 * The effects themselves stay untouched, so gameplay calculations and the server
 * remain authoritative.
 */
public class ClearViewMod extends Mod {

    private static ClearViewMod instance;

    private final BooleanSetting antiBlindness = new BooleanSetting(
            "mod.clearview.blindness.name", "mod.clearview.blindness.description",
            Icon.VISIBILITY, this, true);
    private final BooleanSetting antiDarkness = new BooleanSetting(
            "mod.clearview.darkness.name", "mod.clearview.darkness.description",
            Icon.VISIBILITY, this, true);
    private final BooleanSetting antiNausea = new BooleanSetting(
            "mod.clearview.nausea.name", "mod.clearview.nausea.description",
            Icon.VISIBILITY, this, true);

    public ClearViewMod() {
        super("mod.clearview.name", "mod.clearview.description", Icon.VISIBILITY, ModCategory.RENDER);
        instance = this;
    }

    public static ClearViewMod getInstance() {
        return instance;
    }

    public boolean isAntiBlindness() {
        return antiBlindness.isEnabled();
    }

    public boolean isAntiDarkness() {
        return antiDarkness.isEnabled();
    }

    public boolean isAntiNausea() {
        return antiNausea.isEnabled();
    }
}
