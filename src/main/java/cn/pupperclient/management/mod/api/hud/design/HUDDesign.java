package cn.pupperclient.management.mod.api.hud.design;

import java.awt.Color;
import cn.pupperclient.PupperClient;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.mod.impl.settings.HUDModSettings;
import cn.pupperclient.skia.Skia;
import io.github.humbleui.skija.Font;

/** One rendering contract for every HUD, including legacy style names. */
public abstract class HUDDesign {
    private final String name;
    private ColorPalette cachedPalette;
    private HUDColors cachedColors;
    private float cachedOpacity = -1;
    protected HUDDesign(String name) { this.name = name; }
    protected Color surface(ColorPalette palette) { return palette.getSurfaceContainer(); }
    protected float radius(float requested) { return requested; }
    protected boolean outlined() { return false; }

    public final HUDColors colors() {
        ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();
        float opacity = HUDModSettings.getInstance().getBackgroundOpacity();
        if (cachedPalette != palette || cachedOpacity != opacity) {
            cachedColors = HUDColors.from(palette, surface(palette), opacity);
            cachedPalette = palette;
            cachedOpacity = opacity;
        }
        return cachedColors;
    }

    public void drawBackground(float x, float y, float width, float height, float corner) {
        if (width <= 0 || height <= 0) return;
        float r = Math.min(radius(corner), Math.min(width, height) / 2);
        Skia.drawRoundedRect(x, y, width, height, r, colors().surface());
        if (outlined()) Skia.drawOutline(x, y, width, height, r, 0.75f, colors().outline());
    }

    public void drawText(String text, float x, float y, Font font) {
        Skia.drawText(text, x, y, colors().text(), font);
    }
    public final Color getTextColor() { return colors().text(); }
    public final String getName() { return name; }
}
