package cn.pupperclient.management.mod.api.hud.design.impl;
import java.awt.Color;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.mod.api.hud.design.HUDDesign;
/** Retains the saved key; an outlined surface replaces unreadable floating text. */
public class ClearDesign extends HUDDesign {
    public ClearDesign() { super("design.clear"); }
    @Override protected Color surface(ColorPalette palette) { return palette.getSurface(); }
    @Override protected boolean outlined() { return true; }
}