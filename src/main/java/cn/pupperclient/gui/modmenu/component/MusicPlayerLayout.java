package cn.pupperclient.gui.modmenu.component;

/** One coordinate system for drawing, pointer routing and offscreen specimens. */
public final class MusicPlayerLayout {
    public enum Panel { NONE, LYRICS, QUEUE }
    public record Box(float x, float y, float width, float height) {
        public boolean contains(double mx, double my) { return mx >= x && my >= y && mx < x + width && my < y + height; }
    }
    public static final float WIDTH = 1120, HEIGHT = 720;
    private MusicPlayerLayout() { }
    public static Box content(Panel panel) { return new Box(248, 100, panel == Panel.NONE ? 844 : 528, 500); }
    public static Box sidePanel() { return new Box(796, 100, 300, 500); }
    public static Box popup(double x, double y, float width, int rows) {
        float height = rows * 48 + 16;
        return new Box((float) Math.clamp(x, 8, WIDTH - width - 8),
            (float) Math.clamp(y, 8, 608 - height), width, height);
    }
}
