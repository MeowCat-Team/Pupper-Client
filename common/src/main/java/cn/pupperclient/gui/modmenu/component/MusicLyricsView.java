package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.music.Music;
import cn.pupperclient.management.music.MusicManager;
import cn.pupperclient.management.music.MusicQueue;
import cn.pupperclient.management.music.lyric.LyricsManager;
import cn.pupperclient.management.music.lyric.SongLyrics;
import cn.pupperclient.ui.component.Component;
import org.lwjgl.glfw.GLFW;

/** Automatically follows playback; wheel browsing returns to the current line after five seconds. */
public final class MusicLyricsView extends Component {
    private Music last;
    private float focus;
    private long previousFrame, browseUntil;
    private boolean retryPressed;
    private Music retryTarget;
    private int pressedLine = -1;
    private Music lineTarget;
    private SongLyrics lineDocument;
    private record Selection(Music music, MusicQueue.Entry selected, boolean pending) { }

    public MusicLyricsView(float x, float y, float width, float height) {
        super(x, y); this.width = width; this.height = height;
    }
    public MusicLyricsView(MusicPlayerLayout.Box box) {
        this(box.x(), box.y(), box.width(), box.height());
    }
    public void layout(MusicPlayerLayout.Box box) {
        if (x != box.x() || y != box.y() || width != box.width() || height != box.height()) cancelPointer();
        x = box.x(); y = box.y(); width = box.width(); height = box.height();
    }
    private MusicPlayerLayout.Box bounds() { return new MusicPlayerLayout.Box(x, y, width, height); }
    public void cancelPointer() { retryPressed = false; retryTarget = null; pressedLine = -1; lineTarget = null; lineDocument = null; }
    private static Selection selection(MusicManager manager) {
        Music music = manager.getCurrentMusic();
        var selected = manager.getQueue().snapshot().current();
        boolean pending = MusicNowPlayingUi.pendingSelection(manager.isQueueLoading(),
            selected == null ? null : selected.key(), music == null ? null : MusicQueue.Entry.of(music).key());
        return new Selection(music, selected, pending);
    }
    @Override public void draw(double mx, double my) {
        draw(mx, my, PupperClient.getInstance().getColorManager().getPalette());
    }
    public void draw(double mx, double my, cn.pupperclient.management.color.api.ColorPalette palette) {
        var client = PupperClient.getInstance();
        var manager = client.getMusicManager();
        var selection = selection(manager);
        Music music = selection.music();
        if (selection.pending()) {
            last = null; focus = 0; browseUntil = 0; previousFrame = 0; cancelPointer();
            String title = selection.selected() != null ? selection.selected().track().title() : music == null ? "" : music.getTitle();
            MusicNowPlayingUi.lyrics(bounds(), title, new LyricsManager.Result(LyricsManager.State.LOADING, SongLyrics.EMPTY),
                -1, 0, mx, my, palette);
            return;
        }
        var result = manager.getService().lyrics().get(music);
        SongLyrics lyrics = result.lyrics();
        var options = manager.experience().settings();
        int active = lyrics.currentIndex(manager.getCurrentTime() + options.lyricOffset());
        long now = System.nanoTime();
        float dt = previousFrame == 0 ? 0 : Math.min(.1f, (now - previousFrame) / 1_000_000_000f);
        previousFrame = now;
        if (music != last) { last = music; focus = Math.max(0, active); browseUntil = 0; cancelPointer(); }
        if (now >= browseUntil && lyrics.synced()) focus += (Math.max(0, active) - focus) * (1 - (float) Math.exp(-8 * dt));
        MusicNowPlayingUi.lyrics(bounds(), music == null ? null : music.getTitle(), result, active, focus,
            mx, my, palette, options);
    }
    @Override public void mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!MusicNowPlayingUi.body(bounds()).contains(mx, my) || !Double.isFinite(vertical)) return;
        var manager = PupperClient.getInstance().getMusicManager();
        var selection = selection(manager);
        if (selection.pending()) return;
        var lyrics = manager.getService().lyrics().get(selection.music()).lyrics();
        int count = lyrics.synced() ? lyrics.lines().size() : lyrics.plainText().size();
        if (count == 0) return;
        focus = Math.clamp(focus - (float) vertical, 0, Math.max(0, count - 1));
        browseUntil = System.nanoTime() + 5_000_000_000L;
    }
    @Override public void mousePressed(double mx, double my, int button) {
        cancelPointer();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        var manager = PupperClient.getInstance().getMusicManager();
        var selection = selection(manager);
        if (MusicNowPlayingUi.retry(bounds()).contains(mx, my) && !selection.pending() && selection.music() != null
                && MusicNowPlayingUi.canRetry(selection.music().getTitle(), manager.getService().lyrics().get(selection.music()))) {
            retryPressed = true; retryTarget = selection.music();
            return;
        }
        if (selection.pending() || selection.music() == null) return;
        var document = manager.getService().lyrics().get(selection.music()).lyrics();
        int line = MusicNowPlayingUi.lineAt(bounds(), document, focus, manager.experience().settings(), mx, my);
        if (line >= 0) { pressedLine = line; lineTarget = selection.music(); lineDocument = document; }
    }
    @Override public void mouseReleased(double mx, double my, int button) {
        if (retryPressed && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && MusicNowPlayingUi.retry(bounds()).contains(mx, my)) {
            var manager = PupperClient.getInstance().getMusicManager();
            var selection = selection(manager);
            if (!selection.pending() && selection.music() == retryTarget)
                manager.getService().lyrics().retry(retryTarget);
        }
        if (pressedLine >= 0 && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            var manager = PupperClient.getInstance().getMusicManager();
            var selection = selection(manager);
            var document = manager.getService().lyrics().get(selection.music()).lyrics();
            var options = manager.experience().settings();
            if (!selection.pending() && selection.music() == lineTarget && document == lineDocument
                    && MusicNowPlayingUi.lineAt(bounds(), document, focus, options, mx, my) == pressedLine
                    && manager.seek(Math.max(0, document.lines().get(pressedLine).getTime() - options.lyricOffset()))) {
                focus = pressedLine; browseUntil = 0;
            }
        }
        cancelPointer();
    }
}
