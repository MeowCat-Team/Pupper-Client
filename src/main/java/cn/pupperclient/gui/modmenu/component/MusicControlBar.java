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
    private float beforeMute = .5f;

    public MusicControlBar(float x, float y, float width) {
        super(x, y);
        this.width = width;
        height = 88;
    }

    @Override public void draw(double mouseX, double mouseY) {
        if (adjustingVolume) manager.setVolume((float) Math.clamp((mouseX - (x + width - 108)) / 84, 0, 1));
        Music music = manager.getCurrentMusic();
        boolean available = music != null || !manager.getMusics().isEmpty();
        var palette = PupperClient.getInstance().getColorManager().getPalette();
        MusicUi.playback(x, y, width, new MusicUi.Playback(
            music == null ? MusicText.get("music.player.ready") : music.getTitle(),
            music == null ? MusicText.get("music.player.choose") : music.getArtist(),
            music == null ? null : music.getAlbum(), music != null && manager.isPlaying(), manager.isRepeat(),
            manager.isShuffle(), music != null && manager.getService().isLiked(music.getTrack(), music.getAudio().getName()),
            manager.getVolume(), manager.getCurrentTime(), manager.getEndTime(), available), mouseX, mouseY, palette);
        int hovered = actionAt(mouseX, mouseY);
        if (hovered >= 0) {
            String[] keys = { "music.action.repeat", "music.action.previous",
                manager.isPlaying() ? "music.action.pause" : "music.action.play",
                "music.action.next", "music.action.shuffle", "music.action.like", "music.action.mute" };
            MusicUi.tooltip(MusicText.get(keys[hovered]), mouseX, mouseY, x + width, palette);
        }
    }

    @Override public void mousePressed(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        adjustingVolume = MusicUi.inside(mouseX, mouseY, x + width - 116, y + 8, 104, 60);
        pressed = adjustingVolume ? -1 : actionAt(mouseX, mouseY);
    }

    @Override public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (adjustingVolume) {
            manager.setVolume((float) Math.clamp((mouseX - (x + width - 108)) / 84, 0, 1));
            adjustingVolume = false;
        }
        int action = pressed;
        pressed = -1;
        if (action < 0 || action != actionAt(mouseX, mouseY)) return;
        switch (action) {
            case 0 -> { manager.setShuffle(false); manager.setRepeat(!manager.isRepeat()); }
            case 1 -> manager.back();
            case 2 -> manager.switchPlayBack();
            case 3 -> manager.next();
            case 4 -> { manager.setRepeat(false); manager.setShuffle(!manager.isShuffle()); }
            case 5 -> {
                Music music = manager.getCurrentMusic();
                if (music != null) manager.getService().toggleLike(music.getTrack(), music.getAudio().getName(),
                    _ -> { }, failure -> cn.pupperclient.utils.chat.ChatUtils.error(MusicText.get(failure.key())));
            }
            case 6 -> {
                if (manager.getVolume() > 0) { beforeMute = manager.getVolume(); manager.setVolume(0); }
                else manager.setVolume(beforeMute);
            }
            default -> { }
        }
    }

    private int actionAt(double mx, double my) {
        float center = x + width / 2;
        for (int i = 0; i < 5; i++)
            if (MusicUi.inside(mx, my, center - 120 + i * 48, y + 4, 48, 48)) return i;
        float titleWidth = Math.max(80, Math.min(180, width / 2 - 224));
        if (MusicUi.inside(mx, my, x + 64 + titleWidth, y + 12, 48, 48)) return 5;
        if (MusicUi.inside(mx, my, x + width - 164, y + 12, 48, 48)) return 6;
        return -1;
    }
}
