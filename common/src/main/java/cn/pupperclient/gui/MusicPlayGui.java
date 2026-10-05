package cn.pupperclient.gui;

import cn.pupperclient.PupperClient;
import cn.pupperclient.gui.api.SimplePupperClientGui;
import cn.pupperclient.gui.modmenu.component.*;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.theme.MaterialTheme;
import org.lwjgl.glfw.GLFW;

/** Shared desktop player with focused browsing and cover-driven full-window lyrics. */
public class MusicPlayGui extends SimplePupperClientGui {
    public static final float UI_WIDTH = MusicPlayerLayout.WIDTH, UI_HEIGHT = MusicPlayerLayout.HEIGHT;
    private enum Page { LIBRARY, CLOUD, DOWNLOADS, SETTINGS }
    private Page page = Page.LIBRARY;
    private MusicControlBar controls;
    private MusicLibraryView library;
    private MusicLyricsView lyrics;
    private MusicQueueView queue;
    private MusicAccountDialog account;
    private MusicDownloadsView downloads;
    private MusicSettingsView settings;
    private MusicCloudView cloud;
    private final MusicPopupMenu menu = new MusicPopupMenu();
    private final MusicArtworkPalette artworkPalette = new MusicArtworkPalette();
    private MusicPlayerLayout.Panel panel = MusicPlayerLayout.Panel.NONE;
    private boolean fullscreen;
    private record Press(MusicPlayerLayout.Box box, Runnable action, boolean fullscreen) { }
    private Press pressed;
    private boolean menuPressed, dialogPressed, accountPressed;

    @Override public void init() {
        super.init(); pressed = null;
        if (controls != null) controls.cancelPointer();
        if (lyrics != null) lyrics.cancelPointer();
        if (library == null) {
            page = Page.LIBRARY;
            library = new MusicLibraryView(MusicPlayerLayout.content(panel), MusicLibraryView.Tab.SEARCH, menu);
            var transport = MusicPlayerLayout.transport();
            controls = new MusicControlBar(transport.x(), transport.y(), transport.width(), () -> setFullscreen(!fullscreen), this::toggleQueue);
            lyrics = new MusicLyricsView(MusicPlayerLayout.nowPlaying(UI_WIDTH, UI_HEIGHT).lyrics());
            queue = new MusicQueueView(MusicPlayerLayout.sidePanel(), () -> { setFullscreen(false); navigate(MusicLibraryView.Tab.SEARCH); },
                () -> { setFullscreen(false); setPage(Page.LIBRARY); library.saveQueue(); });
            var manager = PupperClient.getInstance().getMusicManager();
            downloads = new MusicDownloadsView(MusicPlayerLayout.content(panel), manager.getService().downloads(),
                manager.getService()::retryDownload, manager.getService()::retryDownloads);
            settings = new MusicSettingsView(MusicPlayerLayout.content(panel), () -> {
                var target = MusicSettingsUi.control(MusicPlayerLayout.content(panel), MusicSettingsUi.Control.QUALITY);
                library.openQuality(target.x(), target.y() + target.height());
            }, this::accountChanged, library::quality);
            cloud = new MusicCloudView(MusicPlayerLayout.content(panel), collection -> {
                setPage(Page.LIBRARY); library.browseCollection(collection, () -> setPage(Page.CLOUD));
            }, this::openAccount);
            account = new MusicAccountDialog(manager.getService().login(), this::accountChanged);
        }
        layout(viewport());
    }
    private void accountChanged() {
        PupperClient.getInstance().getMusicManager().refreshPrefetch(); library.accountChanged(); cloud.accountChanged();
    }
    private void openAccount() {
        controls.cancelPointer();
        if (!PupperClient.getInstance().getMusicManager().getService().apiConfiguration().snapshot().trustedAccounts()) {
            setPage(Page.SETTINGS); settings.openConnection(); return;
        }
        account.open();
    }
    private Component active() { return switch (page) {
        case LIBRARY -> library; case CLOUD -> cloud; case DOWNLOADS -> downloads; case SETTINGS -> settings;
    }; }
    private String pageId() { return page == Page.LIBRARY ? library.tab().name().toLowerCase(java.util.Locale.ROOT)
        : page.name().toLowerCase(java.util.Locale.ROOT); }
    private void navigate(MusicLibraryView.Tab tab) { setPage(Page.LIBRARY); library.navigate(tab); }
    private void setPage(Page next) {
        if (page == next) return;
        library.blurInput(); settings.blurInput(); controls.cancelPointer(); downloads.cancelPointer(); pressed = null; menu.close();
        if (page == Page.LIBRARY) library.suspend();
        if (page == Page.CLOUD) cloud.close();
        page = next;
        if (page == Page.LIBRARY) library.resume();
        if (page == Page.CLOUD) cloud.show();
        if (page == Page.SETTINGS) settings.show();
    }
    private MusicPlayerLayout.Viewport viewport() {
        return MusicPlayerLayout.fit(client.getWindow().getWidth(), client.getWindow().getHeight(), fullscreen);
    }
    private void layout(MusicPlayerLayout.Viewport viewport) {
        var playing = MusicPlayerLayout.nowPlaying(viewport.width(), viewport.height());
        controls.presentation(fullscreen, panel == MusicPlayerLayout.Panel.QUEUE);
        controls.layout(fullscreen ? playing.transport() : MusicPlayerLayout.transport());
        lyrics.layout(playing.lyrics());
        queue.layout(fullscreen ? playing.lyrics() : MusicPlayerLayout.sidePanel());
        var content = MusicPlayerLayout.content(panel);
        library.layout(content); downloads.layout(content); settings.layout(content); cloud.layout(content);
    }
    private void setFullscreen(boolean next) {
        fullscreen = next; panel = MusicPlayerLayout.Panel.NONE; pressed = null; controls.cancelPointer(); lyrics.cancelPointer(); menu.close(); library.blurInput(); settings.blurInput();
    }
    private void toggleQueue() {
        panel = panel == MusicPlayerLayout.Panel.QUEUE ? MusicPlayerLayout.Panel.NONE : MusicPlayerLayout.Panel.QUEUE; layout(viewport());
    }
    @Override public void draw(double mouseX, double mouseY) {
        var viewport = viewport(); layout(viewport);
        double mx = viewport.localX(mouseX), my = viewport.localY(mouseY);
        var palette = PupperClient.getInstance().getColorManager().getPalette();
        var service = PupperClient.getInstance().getMusicManager().getService();
        Skia.save();
        try {
            Skia.translate(viewport.offsetX(), viewport.offsetY()); Skia.scale(viewport.scale());
            if (fullscreen) {
                var immersive = MusicNowPlayingUi.immersivePalette(palette);
                var playback = controls.state();
                MusicNowPlayingUi.frame(viewport.width(), viewport.height(), playback, mx, my, immersive, artworkPalette.colors(playback.cover()));
                if (panel == MusicPlayerLayout.Panel.QUEUE) queue.draw(mx, my, immersive);
                else lyrics.draw(mx, my, immersive);
                controls.draw(mx, my, immersive); return;
            }
            MaterialTheme.glassPanel(0, 0, UI_WIDTH, UI_HEIGHT, 20, palette);
            String id = pageId();
            MusicUi.sidebar(id, service.provider().id(), library.quality(), mx, my, palette);
            MusicAccountUi.launcher(MusicPlayerLayout.accountButton(), service.login().account(), mx, my, palette);
            MusicUi.browserHeader(id, page == Page.LIBRARY ? library.heading() : MusicText.get("music.tab." + id), panel, mx, my, palette);
            active().draw(mx, my);
            if (panel == MusicPlayerLayout.Panel.QUEUE) {
                var side = MusicPlayerLayout.sidePanel();
                Skia.drawLine(side.x() - 10, side.y(), side.x() - 10, side.y() + side.height(), 1,
                    MaterialTheme.alpha(palette.getOutlineVariant(), .4f)); queue.draw(mx, my);
            }
            controls.draw(mx, my);
            menu.draw(mx, my, palette); library.drawDialog(mx, my); account.draw(mx, my);
        } finally { Skia.restore(); }
    }
    private Press navigationAt(double mx, double my) {
        String[] ids = { "search", "library", "liked", "playlists", "cloud", "recent", "downloads", "netease", "audius", "settings", "account" };
        Runnable[] actions = { () -> navigate(MusicLibraryView.Tab.SEARCH), () -> navigate(MusicLibraryView.Tab.LIBRARY),
            () -> navigate(MusicLibraryView.Tab.LIKED), () -> navigate(MusicLibraryView.Tab.PLAYLISTS), () -> setPage(Page.CLOUD),
            () -> navigate(MusicLibraryView.Tab.RECENT), () -> setPage(Page.DOWNLOADS),
            () -> { library.selectProvider("netease"); navigate(MusicLibraryView.Tab.SEARCH); },
            () -> { library.selectProvider("audius"); navigate(MusicLibraryView.Tab.SEARCH); }, () -> setPage(Page.SETTINGS), this::openAccount };
        for (int i = 0; i < ids.length; i++) {
            var box = MusicPlayerLayout.navBox(ids[i]); if (box.contains(mx, my)) return new Press(box, actions[i], false);
        }
        return null;
    }
    @Override public boolean onMousePressed(double mouseX, double mouseY, int button, boolean doubled) {
        var viewport = viewport(); layout(viewport);
        double mx = viewport.localX(mouseX), my = viewport.localY(mouseY);
        pressed = null; accountPressed = account.isOpen(); dialogPressed = !fullscreen && library.dialogOpen(); menuPressed = menu.isOpen();
        if (accountPressed) { account.mousePressed(mx, my, button); return true; }
        if (dialogPressed) { library.mousePressed(mx, my, button, doubled); return true; }
        if (menuPressed) { menu.mousePressed(mx, my, button); return true; }
        if (fullscreen) {
            var back = MusicPlayerLayout.nowPlaying(viewport.width(), viewport.height()).back();
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && back.contains(mx, my)) pressed = new Press(back, () -> setFullscreen(false), true);
            else {
                if (panel == MusicPlayerLayout.Panel.QUEUE) queue.mousePressed(mx, my, button, doubled);
                else lyrics.mousePressed(mx, my, button);
                controls.mousePressed(mx, my, button);
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) pressed = navigationAt(mx, my);
        if (pressed != null) { library.blurInput(); settings.blurInput(); return true; }
        if (page == Page.LIBRARY) library.mousePressed(mx, my, button, doubled); else active().mousePressed(mx, my, button);
        if (panel == MusicPlayerLayout.Panel.QUEUE) queue.mousePressed(mx, my, button, doubled);
        controls.mousePressed(mx, my, button); return true;
    }
    @Override public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        var viewport = viewport(); layout(viewport);
        double mx = viewport.localX(mouseX), my = viewport.localY(mouseY);
        if (accountPressed) { accountPressed = false; account.mouseReleased(mx, my, button); return true; }
        if (dialogPressed) { dialogPressed = false; library.mouseReleased(mx, my, button); return true; }
        if (menuPressed) { menuPressed = false; menu.mouseReleased(mx, my, button); return true; }
        Press press = pressed; pressed = null;
        if (press != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && fullscreen == press.fullscreen() && press.box().contains(mx, my)) press.action().run(); return true;
        }
        if (fullscreen) { if (panel == MusicPlayerLayout.Panel.QUEUE) queue.mouseReleased(mx, my, button); else lyrics.mouseReleased(mx, my, button); }
        else { active().mouseReleased(mx, my, button); if (panel == MusicPlayerLayout.Panel.QUEUE) queue.mouseReleased(mx, my, button); }
        controls.mouseReleased(mx, my, button); return true;
    }
    @Override public boolean onMouseScrolled(double x, double y, double horizontal, double vertical) {
        var viewport = viewport(); layout(viewport);
        double mx = viewport.localX(x), my = viewport.localY(y);
        if (account.isOpen() || library.dialogOpen()) return true;
        if (menu.isOpen()) { menu.mouseScrolled(mx, my, vertical); return true; }
        if (fullscreen) { if (panel == MusicPlayerLayout.Panel.QUEUE) queue.mouseScrolled(mx, my, horizontal, vertical); else lyrics.mouseScrolled(mx, my, horizontal, vertical); }
        else { active().mouseScrolled(mx, my, horizontal, vertical); if (panel == MusicPlayerLayout.Panel.QUEUE) queue.mouseScrolled(mx, my, horizontal, vertical); }
        return true;
    }
    @Override public boolean onCharTyped(int chr) {
        if (account.isOpen()) account.charTyped(chr);
        else if (!fullscreen && library.dialogOpen()) library.charTyped(chr);
        else if (!fullscreen && !menu.isOpen()) active().charTyped(chr);
        return true;
    }
    @Override public boolean onKeyPressed(int key, int scancode, int modifiers) {
        if (account.isOpen()) { account.keyPressed(key, scancode, modifiers); return true; }
        if (!fullscreen && library.dialogOpen()) { library.keyPressed(key, scancode, modifiers); return true; }
        if (menu.keyPressed(key)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (fullscreen) { setFullscreen(false); return true; }
            if (panel != MusicPlayerLayout.Panel.NONE) { panel = MusicPlayerLayout.Panel.NONE; layout(viewport()); return true; }
            if (page != Page.LIBRARY) { navigate(MusicLibraryView.Tab.SEARCH); return true; }
            if (library.back()) return true;
            return super.onKeyPressed(key, scancode, modifiers);
        }
        if (key == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (fullscreen) setFullscreen(false); navigate(MusicLibraryView.Tab.SEARCH);
        } else if (key == GLFW.GLFW_KEY_SPACE && (fullscreen || !(page == Page.LIBRARY && library.isInputFocused()
                || page == Page.SETTINGS && settings.isInputFocused()))) PupperClient.getInstance().getMusicManager().switchPlayBack();
        else if (!fullscreen) active().keyPressed(key, scancode, modifiers);
        return true;
    }
    @Override public void removed() {
        artworkPalette.close();
        if (account != null) account.dispose(); if (cloud != null) cloud.dispose();
        if (library != null) library.dispose(); if (controls != null) controls.cancelPointer();
        if (lyrics != null) lyrics.cancelPointer(); if (settings != null) settings.blurInput();
        library = null; account = null; pressed = null; menu.close(); menuPressed = dialogPressed = accountPressed = false;
    }
}
