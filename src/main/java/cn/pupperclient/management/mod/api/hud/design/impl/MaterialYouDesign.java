package cn.pupperclient.management.mod.api.hud.design.impl;
import java.awt.Color;
import cn.pupperclient.management.mod.api.hud.design.HUDDesign;
import cn.pupperclient.management.mod.api.hud.design.HUDColors;
import cn.pupperclient.skia.Skia;

/** Material surfaces with a quiet glass rim; accent colors belong to content and states. */
public class MaterialYouDesign extends HUDDesign {
    public MaterialYouDesign() { super("design.materialyou"); }
    @Override protected boolean outlined() { return true; }
    @Override protected Color outlineColor(HUDColors colors) {
        Color outline = colors.outline();
        return new Color(outline.getRed(), outline.getGreen(), outline.getBlue(), 68);
    }

    @Override public void drawBackground(float x, float y, float width, float height, float corner) {
        super.drawBackground(x, y, width, height, corner);
        if (width <= 2 || height <= 2) return;
        float opacity = colors().surface().getAlpha() / 255f;
        if (opacity <= 0 || opacity >= 1) return;
        float r = Math.min(radius(corner), Math.min(width, height) / 2);
        // A clipped inner rim suggests reflected light without tinting the whole panel.
        Skia.save();
        try {
            Skia.clip(x, y, width, height / 2, 0);
            Skia.drawOutline(x + 0.75f, y + 0.75f, width - 1.5f, height - 1.5f,
                Math.max(0, r - 0.75f), 0.65f, new Color(255, 255, 255, Math.round(54 * (1 - opacity))));
        } finally { Skia.restore(); }
    }
}
