package cn.pupperclient.management.mod.api.hud.design.impl;

import java.awt.Color;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.mod.api.hud.design.HUDColors;
import cn.pupperclient.management.mod.api.hud.design.HUDDesign;
import cn.pupperclient.skia.Skia;

/** A translucent, palette-tinted HUD surface with a restrained glass edge. */
public final class GlassDesign extends HUDDesign {
    private static final Color DARK_SHADOW = new Color(0, 0, 0, 38);
    private static final Color LIGHT_SHADOW = new Color(0, 0, 0, 22);
    private static final Color DARK_EDGE = new Color(255, 255, 255, 92);
    private static final Color LIGHT_EDGE = new Color(35, 39, 47, 65);
    private static final Color DARK_SHEEN = new Color(255, 255, 255, 68);
    private static final Color LIGHT_SHEEN = new Color(255, 255, 255, 148);

    public GlassDesign() { super("design.glass"); }

    @Override protected Color surface(ColorPalette palette) {
        return palette.getSurfaceContainerLow();
    }

    @Override public void drawBackground(float x, float y, float width, float height, float corner) {
        if (width <= 0 || height <= 0) return;
        float radius = Math.min(corner, Math.min(width, height) / 2);
        HUDColors colors = colors();
        boolean dark = PupperClient.getInstance().getColorManager().getPalette().isDarkMode();

        // A small offset grounds the transparent panel without a blur pass per widget.
        Skia.drawRoundedRect(x, y + 1.5f, width, height, radius,
            dark ? DARK_SHADOW : LIGHT_SHADOW);
        Skia.drawRoundedRect(x, y, width, height, radius, colors.surface());
        if (width > 2 && height > 2)
            Skia.drawOutline(x, y, width, height, radius, 1,
                dark ? DARK_EDGE : LIGHT_EDGE);

        // The clipped hairline reads as a light-catching edge at chip sizes too.
        if (width > 8 && height > 8) {
            Skia.save();
            try {
                Skia.clip(x, y, width, height, radius);
                Skia.drawRoundedRect(x + radius / 2, y + 0.75f, width - radius, 0.75f, 0.375f,
                    dark ? DARK_SHEEN : LIGHT_SHEEN);
            } finally { Skia.restore(); }
        }
    }
}
