package cn.pupperclient.management.mod.impl.misc;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.skia.font.Icon;

/** Opt-in chat input aid, persisted through the ordinary module configuration. */
public final class MiniMessageCompletionMod extends Mod {
    private static MiniMessageCompletionMod instance;

    public MiniMessageCompletionMod() {
        super("mod.minimessage.name", "mod.minimessage.description", Icon.CODE, ModCategory.MISC);
        instance = this;
    }

    public static boolean isCompletionEnabled() {
        return instance != null && instance.isEnabled();
    }
}
