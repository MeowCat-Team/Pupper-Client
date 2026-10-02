package cn.pupperclient.gui;

import cn.pupperclient.PupperClient;
import cn.pupperclient.gui.api.SimplePupperClientGui;
import cn.pupperclient.gui.modmenu.component.MusicControlBar;
import cn.pupperclient.gui.modmenu.component.MusicLibraryView;
import cn.pupperclient.gui.modmenu.component.MusicUi;
import cn.pupperclient.gui.modmenu.component.MusicLyricsView;
import cn.pupperclient.management.music.Music;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.ui.theme.MaterialTheme;
import org.lwjgl.glfw.GLFW;

/** A cover-led player with shared MD3 cloud search, library, favorites and transport. */
public class MusicPlayGui extends SimplePupperClientGui {
    public static final float UI_WIDTH = 1120;
    public static final float UI_HEIGHT = 720;
    private MusicControlBar controls;
    private MusicLibraryView library;
    private MusicLyricsView lyrics;
    private boolean showLyrics, pressedLyrics;
    private Music pressedFavorite;

    @Override public void init() {
        super.init();
        if (library != null) library.dispose();
        library = new MusicLibraryView(368, 108, UI_WIDTH - 396, 496, MusicLibraryView.Tab.SEARCH);
        controls = new MusicControlBar(28, UI_HEIGHT - 104, UI_WIDTH - 56);
        lyrics = new MusicLyricsView(368, 108, UI_WIDTH - 396, 496);
    }

    @Override public void draw(double mouseX, double mouseY) {
        float scale = scale();
        double mx = localX(mouseX, scale), my = localY(mouseY, scale);
        var palette = PupperClient.getInstance().getColorManager().getPalette();
        var manager = PupperClient.getInstance().getMusicManager();
        Music music = manager.getCurrentMusic();
        Skia.save();
        try {
            Skia.translate(offsetX(scale), offsetY(scale));
            Skia.scale(scale);
            MaterialTheme.glassPanel(0, 0, UI_WIDTH, UI_HEIGHT, MaterialTheme.SURFACE_RADIUS, palette);
            MusicUi.playerHeader(UI_WIDTH, palette);
            MusicUi.lyricsSwitch(UI_WIDTH, showLyrics, mx, my, palette);
            MusicUi.nowPlaying(28, 108, 304, music == null ? null : music.getTrack(),
                music == null ? null : music.getAlbum(), music != null && manager.getService()
                    .isLiked(music.getTrack(), music.getAudio().getName()), mx, my, palette);
            Skia.drawLine(350, 116, 350, 594, 1, MaterialTheme.alpha(palette.getOutlineVariant(), .45f));
            if (showLyrics) lyrics.draw(mx, my); else library.draw(mx, my);
            controls.draw(mx, my);
        } finally { Skia.restore(); }
    }

    @Override public boolean onMousePressed(double mouseX, double mouseY, int button, boolean doubled) {
        float scale = scale();
        double mx = localX(mouseX, scale), my = localY(mouseY, scale);
        pressedLyrics = button == GLFW.GLFW_MOUSE_BUTTON_LEFT && MusicUi.inside(mx, my, UI_WIDTH - 364, 28, 180, 48);
        pressedFavorite = button == GLFW.GLFW_MOUSE_BUTTON_LEFT && MusicUi.inside(mx, my, 28, 562, 304, 48)
            ? PupperClient.getInstance().getMusicManager().getCurrentMusic() : null;
        if (showLyrics) lyrics.mousePressed(mx, my, button); else library.mousePressed(mx, my, button);
        controls.mousePressed(mx, my, button);
        return true;
    }
    @Override public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        float scale = scale();
        double mx = localX(mouseX, scale), my = localY(mouseY, scale);
        boolean toggle = pressedLyrics && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
            && MusicUi.inside(mx, my, UI_WIDTH - 364, 28, 180, 48);
        pressedLyrics = false;
        Music favorite = pressedFavorite;
        pressedFavorite = null;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && favorite != null && MusicUi.inside(mx, my, 28, 562, 304, 48))
            PupperClient.getInstance().getMusicManager().getService().toggleLike(favorite.getTrack(),
                favorite.getAudio().getName(), _ -> { },
                failure -> cn.pupperclient.utils.chat.ChatUtils.error(MusicText.get(failure.key())));
        if (showLyrics) lyrics.mouseReleased(mx, my, button); else library.mouseReleased(mx, my, button);
        if (toggle) showLyrics = !showLyrics;
        controls.mouseReleased(mx, my, button);
        return true;
    }
    @Override public boolean onMouseScrolled(double mx, double my, double horizontal, double vertical) {
        float scale = scale();
        if (showLyrics) lyrics.mouseScrolled(localX(mx, scale), localY(my, scale), horizontal, vertical);
        else library.mouseScrolled(localX(mx, scale), localY(my, scale), horizontal, vertical);
        return true;
    }
    @Override public boolean onCharTyped(int chr) { if (!showLyrics) library.charTyped(chr); return true; }
    @Override public boolean onKeyPressed(int key, int scancode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return super.onKeyPressed(key, scancode, modifiers);
        if (key == GLFW.GLFW_KEY_SPACE && (showLyrics || !library.isInputFocused()))
            PupperClient.getInstance().getMusicManager().switchPlayBack();
        else if (!showLyrics) library.keyPressed(key, scancode, modifiers);
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
