package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.music.Music;
import cn.pupperclient.management.music.lyric.SongLyrics;
import cn.pupperclient.ui.component.Component;
import org.lwjgl.glfw.GLFW;

/** Automatically follows playback; wheel browsing returns to the current line after five seconds. */
public final class MusicLyricsView extends Component {
    private Music last;
    private float focus;
    private long previousFrame, browseUntil;
    private boolean retryPressed;

    public MusicLyricsView(float x, float y, float width, float height) {
        super(x, y); this.width = width; this.height = height;
    }
    @Override public void draw(double mx, double my) {
        var client = PupperClient.getInstance();
        var manager = client.getMusicManager();
        Music music = manager.getCurrentMusic();
        var result = manager.getService().lyrics().get(music);
        SongLyrics lyrics = result.lyrics();
        int active = lyrics.currentIndex(manager.getCurrentTime());
        long now = System.nanoTime();
        float dt = previousFrame == 0 ? 0 : Math.min(.1f, (now - previousFrame) / 1_000_000_000f);
        previousFrame = now;
        if (music != last) { last = music; focus = Math.max(0, active); browseUntil = 0; }
        if (now >= browseUntil && lyrics.synced()) focus += (Math.max(0, active) - focus) * (1 - (float) Math.exp(-8 * dt));
        MusicUi.lyrics(x, y, width, height, music == null ? null : music.getTitle(), result, active, focus,
            mx, my, client.getColorManager().getPalette());
    }
    @Override public void mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!MusicUi.inside(mx, my, x, y + 64, width, height - 108)) return;
        Music music = PupperClient.getInstance().getMusicManager().getCurrentMusic();
        var lyrics = PupperClient.getInstance().getMusicManager().getService().lyrics().get(music).lyrics();
        int count = lyrics.synced() ? lyrics.lines().size() : lyrics.plainText().size();
        focus = Math.clamp(focus - (float) vertical, 0, Math.max(0, count - 1));
        browseUntil = System.nanoTime() + 5_000_000_000L;
    }
    @Override public void mousePressed(double mx, double my, int button) {
        retryPressed = button == GLFW.GLFW_MOUSE_BUTTON_LEFT && MusicUi.inside(mx, my, x + width - 48, y, 48, 48);
    }
    @Override public void mouseReleased(double mx, double my, int button) {
        if (retryPressed && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && MusicUi.inside(mx, my, x + width - 48, y, 48, 48)) {
            var manager = PupperClient.getInstance().getMusicManager();
            manager.getService().lyrics().retry(manager.getCurrentMusic());
        }
        retryPressed = false;
    }
}
