package cn.pupperclient.management.mod.impl.misc;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.network.ServerTextPrivacy;

/** Opt-in because modded servers may intentionally use non-vanilla translations. */
public final class ServerTextPrivacyMod extends Mod {
    public ServerTextPrivacyMod() {
        super("mod.servertextprivacy.name", "mod.servertextprivacy.description", Icon.SHIELD, ModCategory.MISC);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        ServerTextPrivacy.setEnabled(true);
    }

    @Override
    public void onDisable() {
        ServerTextPrivacy.setEnabled(false);
        super.onDisable();
    }
}
