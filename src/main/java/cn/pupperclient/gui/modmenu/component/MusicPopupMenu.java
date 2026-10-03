package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.theme.MaterialTheme;
import java.util.List;
import org.lwjgl.glfw.GLFW;

/** Menus consume outside clicks, preserving the selection and preventing accidental playback. */
public final class MusicPopupMenu {
    public record Item(String text, String icon, boolean enabled, boolean selected, Runnable action) { }
    private List<Item> items = List.of();
    private MusicPlayerLayout.Box box;
    private int pressed = -1, focus, offset;
    private static final int VISIBLE_ROWS = 10;
    public boolean isOpen() { return !items.isEmpty(); }
    public void open(double x, double y, List<Item> choices) {
        items = List.copyOf(choices); box = MusicPlayerLayout.popup(x, y, 288, Math.min(VISIBLE_ROWS, items.size())); pressed = -1; focus = offset = 0;
        while (focus < items.size() - 1 && !items.get(focus).enabled()) focus++;
        offset = Math.max(0, focus - VISIBLE_ROWS + 1);
    }
    public void close() { items = List.of(); pressed = -1; }
    private int at(double mx, double my) {
        if (!isOpen() || !box.contains(mx, my) || my < box.y() + 8 || my >= box.y() + box.height() - 8) return -1;
        return Math.clamp(offset + (int) ((my - box.y() - 8) / 48), 0, items.size() - 1);
    }
    public void mousePressed(double mx, double my, int button) {
        pressed = button == GLFW.GLFW_MOUSE_BUTTON_LEFT ? at(mx, my) : -1;
        if (pressed < 0) close();
    }
    public void mouseReleased(double mx, double my, int button) {
        int action = pressed; pressed = -1;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action >= 0 && action == at(mx, my)) activate(action);
    }
    private void activate(int index) {
        Item item = items.get(index);
        if (!item.enabled()) return;
        close(); item.action().run();
    }
    public boolean keyPressed(int key) {
        if (!isOpen()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) close();
        else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) activate(focus);
        else if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            int direction = key == GLFW.GLFW_KEY_UP ? -1 : 1;
            for (int i = 0; i < items.size(); i++) {
                focus = Math.floorMod(focus + direction, items.size());
                if (items.get(focus).enabled()) break;
            }
            if (focus < offset) offset = focus;
            else if (focus >= offset + VISIBLE_ROWS) offset = focus - VISIBLE_ROWS + 1;
        }
        return true;
    }
    public void mouseScrolled(double mx, double my, double vertical) {
        if (isOpen() && box.contains(mx, my)) {
            offset = Math.clamp(offset - (int) Math.signum(vertical), 0, Math.max(0, items.size() - VISIBLE_ROWS));
            focus = Math.clamp(focus, offset, Math.min(items.size() - 1, offset + VISIBLE_ROWS - 1)); pressed = -1;
        }
    }
    public void draw(double mx, double my, ColorPalette palette) {
        if (!isOpen()) return;
        Skia.drawRoundedRect(box.x(), box.y() + 4, box.width(), box.height(), 16, MaterialTheme.alpha(java.awt.Color.BLACK, .18f));
        Skia.drawRoundedRect(box.x(), box.y(), box.width(), box.height(), 16, palette.getSurface());
        for (int i = offset; i < Math.min(items.size(), offset + VISIBLE_ROWS); i++) {
            Item item = items.get(i); float rowY = box.y() + 8 + (i - offset) * 48;
            if ((at(mx, my) == i || focus == i) && item.enabled()) Skia.drawRoundedRect(box.x() + 4, rowY, box.width() - 8, 48, 12,
                MaterialTheme.alpha(palette.getOnSurface(), .08f));
            var color = MaterialTheme.alpha(palette.getOnSurface(), item.enabled() ? 1 : .38f);
            Skia.drawFullCenteredText(item.icon(), box.x() + 28, rowY + 24, color, Fonts.getIcon(20));
            Skia.drawHeightCenteredText(Skia.getLimitText(item.text(), Fonts.getRegular(14), box.width() - 104), box.x() + 52,
                rowY + 24, color, Fonts.getRegular(14));
            if (item.selected()) Skia.drawFullCenteredText(Icon.CHECK, box.x() + box.width() - 28, rowY + 24,
                palette.getPrimary(), Fonts.getIcon(20));
        }
    }
}
