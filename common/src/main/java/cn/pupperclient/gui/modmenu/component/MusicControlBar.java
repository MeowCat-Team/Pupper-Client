package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.music.Music;
import cn.pupperclient.management.music.MusicManager;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.ui.component.Component;
import org.lwjgl.glfw.GLFW;

/** MD3 transport buttons with 48-unit hit targets and a live volume slider. */
public class MusicControlBar extends Component {
    private final MusicManager manager = PupperClient.getInstance().getMusicManager();
    private int pressed = -1;
    private boolean adjustingVolume;
    private Music seekTarget;
    private long seekGeneration;
    private float seekPosition;
    private float beforeMute = .5f;
    private final Runnable openLyrics;
    private final Runnable openQueue;
    private boolean immersive, queueOpen;

    public MusicControlBar(float x, float y, float width) {
        this(x, y, width, () -> { });
    }
    public MusicControlBar(float x, float y, float width, Runnable openLyrics) {
        this(x, y, width, openLyrics, () -> { });
    }
    public MusicControlBar(float x, float y, float width, Runnable openLyrics, Runnable openQueue) {
        super(x, y);
        this.width = width;
        height = 88;
        this.openLyrics = openLyrics;
        this.openQueue = openQueue;
    }
    public void presentation(boolean immersive, boolean queueOpen) {
        if (this.immersive != immersive) cancelPointer();
        this.immersive = immersive; this.queueOpen = queueOpen;
    }
    public void layout(MusicPlayerLayout.Box bounds) {
        if (x != bounds.x() || y != bounds.y() || width != bounds.width() || height != bounds.height()) cancelPointer();
        x = bounds.x(); y = bounds.y(); width = bounds.width(); height = bounds.height();
    }
    public void cancelPointer() { pressed = -1; adjustingVolume = false; seekTarget = null; }
    public MusicUi.Playback state() {
        Music music = manager.getCurrentMusic();
        var snapshot = manager.getQueue().snapshot();
        var entry = snapshot.current();
        var track = entry != null ? entry.track() : music == null ? null : music.getTrack();
        boolean pending = entry != null && (manager.isQueueLoading() || music == null
            || !entry.key().equals(cn.pupperclient.management.music.MusicQueue.Entry.of(music).key()));
        if (music != null && !pending) track = music.getTrack();
        boolean available = track != null || !snapshot.upcoming().isEmpty() || !manager.getMusics().isEmpty();
        return new MusicUi.Playback(
            track == null ? MusicText.get("music.player.ready") : track.title(),
            manager.isQueueLoading() ? MusicText.get("music.status.loading") : track == null ? MusicText.get("music.player.choose") : MusicText.artist(track),
            pending ? manager.getService().cover(track) : music == null ? null : music.getAlbum(), manager.isPlaying() || manager.isQueueLoading(), manager.getRepeatMode(),
            manager.isShuffle(), track != null && manager.getService().isLiked(track, entry != null ? entry.filename() : music.getAudio().getName()),
            manager.getVolume(), pending ? 0 : seekTarget == music ? seekPosition : manager.getCurrentTime(),
            pending ? track.durationMillis() / 1000f : manager.getEndTime(), available);
    }
    @Override public void draw(double mouseX, double mouseY) {
        draw(mouseX, mouseY, PupperClient.getInstance().getColorManager().getPalette());
    }
    public void draw(double mouseX, double mouseY, cn.pupperclient.management.color.api.ColorPalette palette) {
        if (adjustingVolume) manager.setVolume(volume(mouseX));
        if (seekTarget != null) {
            if (seekTarget != manager.getCurrentMusic() || seekGeneration != manager.getPlaybackGeneration() || manager.isQueueLoading()) seekTarget = null;
            else seekPosition = position(mouseX);
        }
        MusicUi.playback(x, y, width, state(), mouseX, mouseY, palette, immersive, queueOpen);
        int hovered = actionAt(mouseX, mouseY);
        if (hovered >= 0) {
            String[] keys = { manager.getRepeatMode().textKey(), "music.action.previous",
                manager.isPlaying() || manager.isQueueLoading() ? "music.action.pause" : "music.action.play",
                "music.action.next", "music.action.shuffle", "music.action.like", "music.action.mute", "music.lyrics.fullscreen",
                "music.lyrics.fullscreen", queueOpen ? "music.action.closequeue" : "music.action.showqueue" };
            MusicUi.tooltip(MusicText.get(keys[hovered]), mouseX, mouseY, x + width, palette);
        }
    }

    @Override public void mousePressed(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        adjustingVolume = MusicPlayerLayout.volumeHit(bounds(), immersive).contains(mouseX, mouseY);
        pressed = adjustingVolume ? -1 : actionAt(mouseX, mouseY);
        seekTarget = null;
        if (!adjustingVolume && pressed < 0 && manager.getEndTime() > 0 && !manager.isQueueLoading()
                && MusicPlayerLayout.seekHit(bounds(), immersive).contains(mouseX, mouseY)) {
            seekTarget = manager.getCurrentMusic(); seekGeneration = manager.getPlaybackGeneration(); seekPosition = position(mouseX);
        }
    }

    @Override public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (seekTarget != null) {
            Music target = seekTarget; seekTarget = null; pressed = -1;
            if (target == manager.getCurrentMusic() && seekGeneration == manager.getPlaybackGeneration() && !manager.isQueueLoading()) manager.seek(position(mouseX));
            return;
        }
        if (adjustingVolume) {
            manager.setVolume(volume(mouseX));
            adjustingVolume = false;
        }
        int action = pressed;
        pressed = -1;
        if (action < 0 || action != actionAt(mouseX, mouseY)) return;
        switch (action) {
            case 0 -> manager.cycleRepeatMode();
            case 1 -> manager.back();
            case 2 -> manager.switchPlayBack();
            case 3 -> manager.next();
            case 4 -> manager.setShuffle(!manager.isShuffle());
            case 5 -> {
                var entry = manager.getQueue().snapshot().current();
                Music music = manager.getCurrentMusic();
                if (entry != null || music != null) manager.getService().toggleLike(entry != null ? entry.track() : music.getTrack(),
                    entry != null ? entry.filename() : music.getAudio().getName(),
                    _ -> { }, failure -> cn.pupperclient.utils.chat.ChatUtils.error(MusicText.get(failure.key())));
            }
            case 6 -> {
                if (manager.getVolume() > 0) { beforeMute = manager.getVolume(); manager.setVolume(0); }
                else manager.setVolume(beforeMute);
            }
            case 7, 8 -> openLyrics.run();
            case 9 -> openQueue.run();
            default -> { }
        }
    }

    private MusicPlayerLayout.Box bounds() { return new MusicPlayerLayout.Box(x, y, width, height); }
    private float position(double mx) {
        var track = MusicPlayerLayout.seekTrack(bounds(), immersive);
        return (float) Math.clamp((mx - track.x()) / track.width(), 0, 1) * manager.getEndTime();
    }

    private int actionAt(double mx, double my) {
        if (!immersive && MusicPlayerLayout.coverArtwork(bounds()).contains(mx, my)
                && (manager.getCurrentMusic() != null || manager.getQueue().snapshot().current() != null)) return 7;
        for (int i = 0; i <= 9; i++) {
            if (i == 7 || immersive && i == 8) continue;
            if (MusicPlayerLayout.playbackAction(bounds(), i, immersive).contains(mx, my)) return i;
        }
        return -1;
    }
    private float volume(double mx) {
        var track = MusicPlayerLayout.volumeTrack(bounds(), immersive);
        return (float) Math.clamp((mx - track.x()) / track.width(), 0, 1);
    }
}
