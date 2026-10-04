package cn.pupperclient.management.mod.impl.render;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.skia.font.Icon;
import org.jetbrains.annotations.NotNull;

@NotNull
public class NoHurtFov extends Mod {
    private static NoHurtFov instance;
    public BooleanSetting nohurtFov = new BooleanSetting("mod.nofov.nohurtFov.name", "mod.nofov.nohurtFov.description", Icon.DISPLAY_SETTINGS, this, true);

    public NoHurtFov() {
        super("mod.nohurtfov.name", "mod.nohurtfov.description", Icon.DISPLAY_SETTINGS, ModCategory.RENDER);

        instance = this;
    }

    public static NoHurtFov getInstance() {
        return instance;
    }
}
