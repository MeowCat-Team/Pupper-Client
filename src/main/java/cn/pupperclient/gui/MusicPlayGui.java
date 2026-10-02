package cn.pupperclient.gui;

import cn.pupperclient.PupperClient;
import cn.pupperclient.gui.api.SimplePupperClientGui;
import cn.pupperclient.gui.modmenu.component.*;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.ui.theme.MaterialTheme;
import org.lwjgl.glfw.GLFW;

/** Desktop music browsing with persistent transport and independent lyrics/Playing Next panels. */
public class MusicPlayGui extends SimplePupperClientGui {
    public static final float UI_WIDTH = MusicPlayerLayout.WIDTH, UI_HEIGHT = MusicPlayerLayout.HEIGHT;
    private MusicControlBar controls;
    private MusicLibraryView library;
    private MusicLyricsView lyrics;
    private MusicQueueView queue;
    private final MusicPopupMenu menu = new MusicPopupMenu();
    private MusicPlayerLayout.Panel panel = MusicPlayerLayout.Panel.NONE;
    private record Press(MusicPlayerLayout.Box box, Runnable action) { }
    private Press pressed;
    private boolean menuPressed;

    @Override public void init() {
        super.init();
        if (library != null) library.dispose();
        library = new MusicLibraryView(MusicPlayerLayout.content(panel), MusicLibraryView.Tab.SEARCH, menu);
        controls = new MusicControlBar(16, 616, 1088);
        var side = MusicPlayerLayout.sidePanel();
        lyrics = new MusicLyricsView(side.x(), side.y(), side.width(), side.height());
        queue = new MusicQueueView(side);
    }
    private void toggle(MusicPlayerLayout.Panel next) {
        panel = panel == next ? MusicPlayerLayout.Panel.NONE : next;
        library.layout(MusicPlayerLayout.content(panel));
    }
    @Override public void draw(double mouseX, double mouseY) {
        float scale = scale(); double mx = localX(mouseX, scale), my = localY(mouseY, scale);
        var palette = PupperClient.getInstance().getColorManager().getPalette();
        var service = PupperClient.getInstance().getMusicManager().getService();
        Skia.save();
        try {
            Skia.translate(offsetX(scale), offsetY(scale)); Skia.scale(scale);
            MaterialTheme.glassPanel(0, 0, UI_WIDTH, UI_HEIGHT, MaterialTheme.SURFACE_RADIUS, palette);
            String page = library.tab().name().toLowerCase(java.util.Locale.ROOT);
            MusicUi.sidebar(page, service.provider().id(), library.quality(), mx, my, palette);
            MusicUi.browserHeader(page, MusicText.get(library.tab() == MusicLibraryView.Tab.SEARCH
                ? service.provider().nameKey() : library.tab() == MusicLibraryView.Tab.LIKED ? "music.sidebar.favorites" : "music.library.subtitle"),
                panel, mx, my, palette);
            library.draw(mx, my);
            if (panel != MusicPlayerLayout.Panel.NONE) {
                Skia.drawLine(786, 100, 786, 600, 1, MaterialTheme.alpha(palette.getOutlineVariant(), .4f));
                if (panel == MusicPlayerLayout.Panel.LYRICS) lyrics.draw(mx, my); else queue.draw(mx, my);
            }
            controls.draw(mx, my);
            if (MusicUi.inside(mx, my, 992, 24, 48, 48)) MusicUi.tooltip(MusicText.get(panel == MusicPlayerLayout.Panel.LYRICS
                ? "music.lyrics.hide" : "music.lyrics.show"), mx, my, UI_WIDTH, palette);
            if (MusicUi.inside(mx, my, 1044, 24, 48, 48)) MusicUi.tooltip(MusicText.get(panel == MusicPlayerLayout.Panel.QUEUE
                ? "music.action.closequeue" : "music.action.showqueue"), mx, my, UI_WIDTH, palette);
            menu.draw(mx, my, palette);
        } finally { Skia.restore(); }
    }
    private Press navigationAt(double mx, double my) {
        MusicPlayerLayout.Box[] boxes = { new MusicPlayerLayout.Box(16, 116, 208, 48), new MusicPlayerLayout.Box(16, 224, 208, 48),
            new MusicPlayerLayout.Box(16, 280, 208, 48), new MusicPlayerLayout.Box(16, 384, 208, 48),
            new MusicPlayerLayout.Box(16, 440, 208, 48), new MusicPlayerLayout.Box(16, 520, 208, 48),
            new MusicPlayerLayout.Box(992, 24, 48, 48), new MusicPlayerLayout.Box(1044, 24, 48, 48) };
        Runnable[] actions = { () -> library.navigate(MusicLibraryView.Tab.SEARCH), () -> library.navigate(MusicLibraryView.Tab.LIBRARY),
            () -> library.navigate(MusicLibraryView.Tab.LIKED), () -> { library.selectProvider("netease"); library.navigate(MusicLibraryView.Tab.SEARCH); },
            () -> { library.selectProvider("audius"); library.navigate(MusicLibraryView.Tab.SEARCH); }, () -> library.openQuality(16, 568),
            () -> toggle(MusicPlayerLayout.Panel.LYRICS), () -> toggle(MusicPlayerLayout.Panel.QUEUE) };
        for (int i = 0; i < boxes.length; i++) if (boxes[i].contains(mx, my)) return new Press(boxes[i], actions[i]);
        return null;
    }
    @Override public boolean onMousePressed(double mouseX, double mouseY, int button, boolean doubled) {
        float scale = scale(); double mx = localX(mouseX, scale), my = localY(mouseY, scale);
        pressed = null; menuPressed = menu.isOpen();
        if (menuPressed) { menu.mousePressed(mx, my, button); return true; }
        // Forward outside presses to the search input so navigation and transport dismiss its focus.
        library.mousePressed(mx, my, button, doubled);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) pressed = navigationAt(mx, my);
        if (pressed != null) return true;
        if (panel == MusicPlayerLayout.Panel.LYRICS) lyrics.mousePressed(mx, my, button);
        else if (panel == MusicPlayerLayout.Panel.QUEUE) queue.mousePressed(mx, my, button, doubled);
        controls.mousePressed(mx, my, button);
        return true;
    }
    @Override public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        float scale = scale(); double mx = localX(mouseX, scale), my = localY(mouseY, scale);
        if (menuPressed) { menuPressed = false; menu.mouseReleased(mx, my, button); return true; }
        Press press = pressed; pressed = null;
        library.mouseReleased(mx, my, button);
        if (press != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && press.box().contains(mx, my)) press.action().run();
            return true;
        }
        if (panel == MusicPlayerLayout.Panel.LYRICS) lyrics.mouseReleased(mx, my, button);
        else if (panel == MusicPlayerLayout.Panel.QUEUE) queue.mouseReleased(mx, my, button);
        controls.mouseReleased(mx, my, button); return true;
    }
    @Override public boolean onMouseScrolled(double x, double y, double horizontal, double vertical) {
        if (menu.isOpen()) return true;
        float scale = scale(); double mx = localX(x, scale), my = localY(y, scale);
        library.mouseScrolled(mx, my, horizontal, vertical);
        if (panel == MusicPlayerLayout.Panel.LYRICS) lyrics.mouseScrolled(mx, my, horizontal, vertical);
        else if (panel == MusicPlayerLayout.Panel.QUEUE) queue.mouseScrolled(mx, my, horizontal, vertical);
        return true;
    }
    @Override public boolean onCharTyped(int chr) { if (!menu.isOpen()) library.charTyped(chr); return true; }
    @Override public boolean onKeyPressed(int key, int scancode, int modifiers) {
        if (menu.keyPressed(key)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (panel != MusicPlayerLayout.Panel.NONE) { panel = MusicPlayerLayout.Panel.NONE; library.layout(MusicPlayerLayout.content(panel)); return true; }
            return super.onKeyPressed(key, scancode, modifiers);
        }
        if (key == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) library.navigate(MusicLibraryView.Tab.SEARCH);
        else if (key == GLFW.GLFW_KEY_SPACE && !library.isInputFocused()) PupperClient.getInstance().getMusicManager().switchPlayBack();
        else library.keyPressed(key, scancode, modifiers);
        return true;
    }
    @Override public void removed() { if (library != null) library.dispose(); }
    private float scale() {
        return Math.min(1, Math.min(Math.max(1, client.getWindow().getWidth() - 48) / UI_WIDTH,
            Math.max(1, client.getWindow().getHeight() - 48) / UI_HEIGHT));
    }
    private float offsetX(float scale) { return (client.getWindow().getWidth() - UI_WIDTH * scale) / 2; }
    private float offsetY(float scale) { return (client.getWindow().getHeight() - UI_HEIGHT * scale) / 2; }
    private double localX(double x, float scale) { return (x - offsetX(scale)) / scale; }
    private double localY(double y, float scale) { return (y - offsetY(scale)) / scale; }
}
