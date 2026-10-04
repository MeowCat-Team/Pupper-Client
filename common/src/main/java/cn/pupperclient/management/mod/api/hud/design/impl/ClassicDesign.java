package cn.pupperclient.management.mod.api.hud.design.impl;
import cn.pupperclient.management.mod.api.hud.design.HUDDesign;
public class ClassicDesign extends HUDDesign {
    public ClassicDesign() { super("design.classic"); }
    @Override protected float radius(float requested) { return Math.min(4, requested); }
    @Override protected boolean outlined() { return true; }
}