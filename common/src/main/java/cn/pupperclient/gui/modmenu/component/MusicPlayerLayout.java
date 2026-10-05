package cn.pupperclient.gui.modmenu.component;

/** One coordinate system for drawing, pointer routing and offscreen specimens. */
public final class MusicPlayerLayout {
    public enum Panel { NONE, LYRICS, QUEUE }
    public record Box(float x, float y, float width, float height) {
        public boolean contains(double mx, double my) { return mx >= x && my >= y && mx < x + width && my < y + height; }
    }
    /** The same transform must be used by painting and every pointer event. */
    public record Viewport(float scale, float offsetX, float offsetY, float width, float height) {
        public double localX(double screenX) { return (screenX - offsetX) / scale; }
        public double localY(double screenY) { return (screenY - offsetY) / scale; }
        public double screenX(double localX) { return offsetX + localX * scale; }
        public double screenY(double localY) { return offsetY + localY * scale; }
    }
    public record NowPlaying(Box artwork, Box metadata, Box lyrics, Box back, Box transport) { }
    public static final float WIDTH = 1360, HEIGHT = 820;
    private MusicPlayerLayout() { }
    public static Box content(Panel panel) {
        return new Box(224, 100, panel == Panel.NONE ? WIDTH - 248 : sidePanel().x() - 244, HEIGHT - 220);
    }
    public static Box sidePanel() { return new Box(WIDTH - 368, 100, 344, HEIGHT - 220); }
    public static Box transport() { return transport(WIDTH, HEIGHT); }
    public static Box transport(float width, float height) { return new Box(16, height - 104, width - 32, 88); }
    public static Box lyricsButton() { return playbackAction(transport(), 8, false); }
    public static Box queueButton() { return playbackAction(transport(), 9, false); }
    public static Box settingsButton() { return new Box(12, HEIGHT - 176, 48, 48); }
    public static Box accountButton() { return new Box(68, HEIGHT - 176, 128, 48); }
    public static Box navBox(String id) {
        return switch (id) {
            case "settings" -> settingsButton(); case "account" -> accountButton();
            default -> new Box(12, switch (id) {
                case "search" -> 100; case "library" -> 192; case "liked" -> 240; case "playlists" -> 288;
                case "cloud" -> 336; case "recent" -> 384; case "downloads" -> 432;
                case "netease" -> 528; case "audius" -> 576;
                default -> throw new IllegalArgumentException("Unknown music navigation");
            }, 184, 48);
        };
    }
    public static Box coverArtwork(Box transport) { return new Box(transport.x() + 12, transport.y() + 12, 48, 48); }
    public static Box seekTrack(Box transport) {
        return seekTrack(transport, false);
    }
    public static Box seekTrack(Box transport, boolean immersive) {
        if (immersive) return new Box(transport.x(), transport.y() + 12, transport.width(), 4);
        float width = Math.min(256, transport.width() / 3);
        return new Box(transport.x() + (transport.width() - width) / 2, transport.y() + 65, width, 4);
    }
    public static Box seekHit(Box transport) {
        return seekHit(transport, false);
    }
    public static Box seekHit(Box transport, boolean immersive) {
        if (immersive) return new Box(transport.x(), transport.y(), transport.width(), 48);
        var track = seekTrack(transport); return new Box(track.x(), transport.y() + 44, track.width(), 44);
    }
    public static Box volumeTrack(Box transport, boolean immersive) {
        return immersive ? new Box(transport.x() + 56, transport.y() + 142, transport.width() - 112, 4)
            : new Box(transport.x() + transport.width() - 108, transport.y() + 36, 84, 4);
    }
    public static Box volumeHit(Box transport, boolean immersive) {
        var track = volumeTrack(transport, immersive);
        return new Box(track.x() - 8, track.y() - 22, track.width() + 16, 48);
    }
    public static Box playbackAction(Box box, int action, boolean immersive) {
        float center = box.x() + box.width() / 2;
        if (action < 5) return new Box(center - 120 + action * 48, box.y() + (immersive ? 56 : 4), 48, 48);
        return switch (action) {
            case 5 -> immersive ? new Box(box.x() + box.width() - 48, box.y() + 120, 48, 48)
                : new Box(box.x() + 64 + Math.max(80, Math.min(180, box.width() / 2 - 224)), box.y() + 12, 48, 48);
            case 6 -> new Box(immersive ? box.x() : box.x() + box.width() - 164, box.y() + (immersive ? 120 : 12), 48, 48);
            case 7 -> coverArtwork(box);
            case 8 -> new Box(box.x() + box.width() - 272, box.y() + 12, 48, 48);
            case 9 -> new Box(immersive ? box.x() + box.width() - 48 : box.x() + box.width() - 220,
                box.y() + (immersive ? 56 : 12), 48, 48);
            default -> throw new IllegalArgumentException("Unknown playback action");
        };
    }
    public static Box nameDialog() { return new Box((WIDTH - 480) / 2, (HEIGHT - 248) / 2, 480, 248); }
    public static Box nameInput() { var dialog = nameDialog(); return new Box(dialog.x() + 24, dialog.y() + 90, 432, 42); }
    public static Box nameCancel() { var dialog = nameDialog(); return new Box(dialog.x() + 172, dialog.y() + 176, 136, 48); }
    public static Box nameSave() { var dialog = nameDialog(); return new Box(dialog.x() + 320, dialog.y() + 176, 136, 48); }
    public static Viewport fit(float frameWidth, float frameHeight, boolean fullscreen) {
        float width = Math.max(1, frameWidth), height = Math.max(1, frameHeight);
        if (fullscreen) {
            float scale = Math.min(width / WIDTH, height / HEIGHT);
            return new Viewport(scale, 0, 0, width / scale, height / scale);
        }
        float scale = Math.min(1.35f, Math.min(Math.max(1, width - 48) / WIDTH, Math.max(1, height - 48) / HEIGHT));
        return new Viewport(scale, (width - WIDTH * scale) / 2, (height - HEIGHT * scale) / 2, WIDTH, HEIGHT);
    }
    public static NowPlaying nowPlaying(float width, float height) {
        float artworkSize = Math.min(Math.clamp(width * .3f, 360, 480), height - 420);
        float artworkY = Math.max(80, (height - artworkSize - 328) / 2);
        float columnX = 56, lyricsX = columnX + artworkSize + Math.max(88, width * .065f);
        return new NowPlaying(new Box(columnX, artworkY, artworkSize, artworkSize),
            new Box(columnX, artworkY + artworkSize + 24, artworkSize, 104),
            new Box(lyricsX, 56, width - lyricsX - 56, height - 88), new Box(24, 16, 48, 48),
            new Box(columnX, artworkY + artworkSize + 140, artworkSize, 176));
    }
    public static Box searchType(float x, float y, int index) { return new Box(x + index * 144, y + 48, 136, 48); }
    public static float listOffset(boolean search, boolean browse) { return browse ? 160 : search ? 136 : 80; }
    public static Box popup(double x, double y, float width, int rows) {
        float height = rows * 48 + 16;
        return new Box((float) Math.clamp(x, 8, WIDTH - width - 8),
            (float) Math.clamp(y, 8, transport().y() - 8 - height), width, height);
    }
}
