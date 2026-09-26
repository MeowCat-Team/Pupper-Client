package cn.pupperclient.management.mod.api.hud.design.impl;
import java.awt.Color;
import cn.pupperclient.management.mod.api.hud.design.HUDDesign;
import cn.pupperclient.management.mod.api.hud.design.HUDColors;
/** Emphasis belongs to content and states, rather than a decorative gradient. */
public class MaterialYouDesign extends HUDDesign {
    public MaterialYouDesign() { super("design.materialyou"); }
    @Override protected boolean outlined() { return true; }
    @Override protected Color outlineColor(HUDColors colors) {
        Color outline = colors.outline();
        return new Color(outline.getRed(), outline.getGreen(), outline.getBlue(), 68);
    }
}
