package cn.pupperclient.management.mod.api.hud.design.impl;
import java.awt.Color;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.mod.api.hud.design.HUDDesign;
public class SimpleDesign extends HUDDesign {
    public SimpleDesign() { super("design.simple"); }
    @Override protected Color surface(ColorPalette palette) { return palette.getSurfaceContainerLow(); }
}