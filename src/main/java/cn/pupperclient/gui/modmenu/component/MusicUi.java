package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.management.music.MusicTrack;
import cn.pupperclient.management.music.lyric.LyricsManager;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.theme.MaterialControls;
import cn.pupperclient.ui.theme.MaterialTheme;
import java.io.File;

/** Stateless player painters, also used by the raster layout preview. */
public final class MusicUi {
    public record Playback(String title, String artist, File cover, boolean playing, boolean repeat, boolean shuffle,
            boolean liked, float volume, float current, float end, boolean enabled) { }
    private MusicUi() { }

    public static void playerHeader(float width, ColorPalette palette) {
        Skia.drawFullCenteredText(Icon.MUSIC_NOTE, 52, 52, palette.getPrimary(), Fonts.getIconFill(28));
        Skia.drawText(MusicText.get("music.player.title"), 82, 28, palette.getOnSurface(), Fonts.getMedium(28));
        Skia.drawText(MusicText.get("music.player.subtitle"), 82, 67, palette.getOnSurfaceVariant(), Fonts.getRegular(13));
        Skia.drawText(MusicText.get("music.player.closehint"), width - 148, 44,
            palette.getOnSurfaceVariant(), Fonts.getRegular(12));
    }

    public static void nowPlaying(float x, float y, float width, MusicTrack track, File cover, boolean liked,
            double mouseX, double mouseY, ColorPalette palette) {
        Skia.drawText(MusicText.get("music.nowplaying"), x + 4, y, palette.getOnSurfaceVariant(), Fonts.getMedium(12));
        artwork(cover, x, y + 28, width, palette);
        String title = track == null ? MusicText.get("music.player.ready") : track.title();
        String artist = track == null ? MusicText.get("music.player.choose") : track.artist();
        Skia.drawText(Skia.getLimitText(title, Fonts.getMedium(24), width), x, y + width + 50,
            palette.getOnSurface(), Fonts.getMedium(24));
        Skia.drawText(Skia.getLimitText(artist, Fonts.getRegular(14), width), x, y + width + 88,
            palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        Skia.drawText(Skia.getLimitText(track == null ? "" : track.album(), Fonts.getRegular(12), width),
            x, y + width + 116, palette.getOnSurfaceVariant(), Fonts.getRegular(12));
        if (track != null) tab(x, y + width + 150, width, Icon.FAVORITE,
            MusicText.get(liked ? "music.action.liked" : "music.action.like"), liked,
            inside(mouseX, mouseY, x, y + width + 150, width, 48), palette);
    }

    public static void playback(float x, float y, float width, Playback state, double mouseX, double mouseY,
            ColorPalette palette) {
        MaterialTheme.card(x, y, width, 88, 24, palette);
        artwork(state.cover(), x + 12, y + 12, 48, palette);
        float titleWidth = Math.max(80, Math.min(180, width / 2 - 224));
        Skia.drawText(Skia.getLimitText(state.title(), Fonts.getMedium(14), titleWidth), x + 72, y + 18,
            palette.getOnSurface(), Fonts.getMedium(14));
        Skia.drawText(Skia.getLimitText(state.artist(), Fonts.getRegular(12), titleWidth), x + 72, y + 42,
            palette.getOnSurfaceVariant(), Fonts.getRegular(12));
        iconButton(x + 64 + titleWidth, y + 12, Icon.FAVORITE, state.liked(), state.enabled(),
            inside(mouseX, mouseY, x + 64 + titleWidth, y + 12, 48, 48), palette);
        float center = x + width / 2;
        String[] icons = { state.repeat() ? Icon.REPEAT_ONE : Icon.REPEAT, Icon.SKIP_PREVIOUS, state.playing() ? Icon.PAUSE : Icon.PLAY_ARROW,
            Icon.SKIP_NEXT, Icon.SHUFFLE };
        for (int i = 0; i < icons.length; i++) {
            boolean selected = i == 0 && state.repeat() || i == 4 && state.shuffle() || i == 2;
            float buttonX = center - 120 + i * 48;
            iconButton(buttonX, y + 4, icons[i], selected, state.enabled(),
                inside(mouseX, mouseY, buttonX, y + 4, 48, 48), palette);
        }
        float progress = state.end() > 0 && Float.isFinite(state.current())
            ? Math.clamp(state.current() / state.end(), 0, 1) : 0;
        float trackWidth = Math.min(256, width / 3);
        float trackX = center - trackWidth / 2;
        Skia.drawRoundedRect(trackX, y + 65, trackWidth, 4, 2, MaterialTheme.surface(palette.getSecondaryContainer()));
        Skia.drawRoundedRect(trackX, y + 65, progress * trackWidth, 4, 2, palette.getPrimary());
        Skia.drawHeightCenteredText(MusicText.time(state.current()), trackX - 42, y + 67,
            palette.getOnSurfaceVariant(), Fonts.getRegular(11));
        Skia.drawHeightCenteredText(MusicText.time(state.end()), trackX + trackWidth + 10, y + 67,
            palette.getOnSurfaceVariant(), Fonts.getRegular(11));
        iconButton(x + width - 164, y + 12, state.volume() == 0 ? Icon.VOLUME_OFF : Icon.VOLUME_UP,
            false, true, inside(mouseX, mouseY, x + width - 164, y + 12, 48, 48), palette);
        MaterialControls.slider(x + width - 108, y + 18, 84, palette, MaterialTheme.opacity(), state.volume(),
            inside(mouseX, mouseY, x + width - 116, y + 8, 104, 60) ? 1 : 0, Math.round(state.volume() * 100) + "%");
    }

    public static void lyricsSwitch(float width, boolean selected, double mx, double my, ColorPalette palette) {
        tab(width - 364, 28, 180, Icon.LYRICS, MusicText.get(selected ? "music.lyrics.library" : "music.lyrics.show"),
            selected, inside(mx, my, width - 364, 28, 180, 48), palette);
    }

    public static void lyrics(float x, float y, float width, float height, String title, LyricsManager.Result result,
            int active, float focus, double mx, double my, ColorPalette palette) {
        Skia.drawText(MusicText.get("music.lyrics.title"), x + 12, y + 12, palette.getOnSurface(), Fonts.getMedium(22));
        iconButton(x + width - 48, y, Icon.REFRESH, false, title != null,
            inside(mx, my, x + width - 48, y, 48, 48), palette);
        float top = y + 64, bodyHeight = height - 108;
        var document = result.lyrics();
        if (title == null || document.isEmpty()) {
            String key = title == null ? "music.lyrics.choose" : switch (result.state()) {
                case LOADING -> "music.lyrics.loading";
                case ERROR -> "music.lyrics.error";
                default -> "music.lyrics.empty";
            };
            Skia.drawFullCenteredText(Icon.LYRICS, x + width / 2, top + bodyHeight / 2 - 28,
                palette.getPrimary(), Fonts.getIcon(36));
            Skia.drawCenteredText(MusicText.get(key), x + width / 2, top + bodyHeight / 2 + 30,
                palette.getOnSurfaceVariant(), Fonts.getRegular(16));
        } else {
            int count = document.synced() ? document.lines().size() : document.plainText().size();
            float rowHeight = 80;
            Skia.save();
            try {
                Skia.clip(x, top, width, bodyHeight, 16);
                int first = Math.max(0, (int) Math.floor(focus - bodyHeight / rowHeight / 2) - 1);
                int last = Math.min(count, (int) Math.ceil(focus + bodyHeight / rowHeight / 2) + 2);
                for (int i = first; i < last; i++) {
                    float lineY = top + bodyHeight / 2 + (i - focus) * rowHeight - 28;
                    boolean selected = document.synced() && i == active;
                    if (selected) Skia.drawRoundedRect(x + 4, lineY - 8, width - 8, 72, 16,
                        MaterialTheme.alpha(palette.getSecondaryContainer(), .5f));
                    String text = document.synced() ? document.lines().get(i).getText() : document.plainText().get(i);
                    var font = selected ? Fonts.getMedium(24) : Fonts.getRegular(20);
                    Skia.drawText(Skia.getLimitText(text.isBlank() ? "♪" : text, font, width - 48), x + 24, lineY,
                        selected ? palette.getPrimary() : palette.getOnSurfaceVariant(), font);
                    if (document.synced() && !document.lines().get(i).getTranslation().isBlank())
                        Skia.drawText(Skia.getLimitText(document.lines().get(i).getTranslation(), Fonts.getRegular(14), width - 48),
                            x + 24, lineY + 34, palette.getOnSurfaceVariant(), Fonts.getRegular(14));
                }
            } finally { Skia.restore(); }
        }
        if (!document.isEmpty()) Skia.drawHeightCenteredText(MusicText.get(document.synced()
            ? "music.lyrics.follow" : "music.lyrics.unsynced"), x + 12, y + height - 22,
            palette.getOnSurfaceVariant(), Fonts.getRegular(12));
    }

    public static void iconButton(float x, float y, String icon, boolean selected, boolean enabled,
            boolean hovered, ColorPalette palette) {
        if (selected) Skia.drawRoundedRect(x + 4, y + 4, 40, 40, 20,
            MaterialTheme.surface(palette.getSecondaryContainer()));
        if (hovered && enabled) Skia.drawCircle(x + 24, y + 24, 20,
            MaterialTheme.alpha(palette.getOnSurface(), .08f));
        Skia.drawFullCenteredText(icon, x + 24, y + 24,
            MaterialTheme.alpha(selected ? palette.getPrimary() : palette.getOnSurfaceVariant(), enabled ? 1 : .38f),
            selected ? Fonts.getIconFill(22) : Fonts.getIcon(22));
    }

    public static void tab(float x, float y, float width, String icon, String label, boolean selected,
            boolean hovered, ColorPalette palette) {
        if (selected) Skia.drawRoundedRect(x, y, width, 48, 24,
            MaterialTheme.surface(palette.getSecondaryContainer()));
        if (hovered) Skia.drawRoundedRect(x, y, width, 48, 24,
            MaterialTheme.alpha(palette.getOnSurface(), .08f));
        var color = selected ? palette.getOnSecondaryContainer() : palette.getOnSurfaceVariant();
        Skia.drawFullCenteredText(icon, x + 26, y + 24, color,
            selected ? Fonts.getIconFill(20) : Fonts.getIcon(20));
        Skia.drawHeightCenteredText(Skia.getLimitText(label, Fonts.getMedium(14), width - 62),
            x + 46, y + 24, color, Fonts.getMedium(14));
    }

    public static void artwork(File cover, float x, float y, float size, ColorPalette palette) {
        if (cover != null && cover.isFile()) {
            Skia.drawRoundedImage(cover, x, y, size, size, Math.min(20, size / 5));
        } else {
            Skia.drawRoundedRect(x, y, size, size, Math.min(20, size / 5),
                MaterialTheme.surface(palette.getPrimaryContainer()));
            Skia.drawFullCenteredText(Icon.MUSIC_NOTE, x + size / 2, y + size / 2,
                palette.getOnPrimaryContainer(), Fonts.getIconFill(size * .30f));
        }
    }

    public static void row(float x, float y, float width, File cover, String title, String subtitle,
            String duration, boolean active, boolean playing, boolean liked, boolean saved, int progress,
            boolean hovered, boolean likesEnabled, double mouseX, double mouseY, ColorPalette palette) {
        row(x, y, width, cover, title, subtitle, duration, active, playing, liked, saved, progress,
            hovered, likesEnabled, true, true, mouseX, mouseY, palette);
    }

    public static void row(float x, float y, float width, File cover, String title, String subtitle,
            String duration, boolean active, boolean playing, boolean liked, boolean saved, int progress,
            boolean hovered, boolean likesEnabled, boolean playable, boolean downloadable,
            double mouseX, double mouseY, ColorPalette palette) {
        if (active || hovered) Skia.drawRoundedRect(x, y, width, 72, 16,
            MaterialTheme.alpha(active ? palette.getSecondaryContainer() : palette.getOnSurface(), active ? .5f : .06f));
        artwork(cover, x + 12, y + 12, 48, palette);
        if (hovered || active) {
            Skia.drawRoundedRect(x + 12, y + 12, 48, 48, 10, MaterialTheme.alpha(palette.getSurface(), .72f));
            Skia.drawFullCenteredText(playing && active ? Icon.PAUSE : Icon.PLAY_ARROW, x + 36, y + 36,
                playable ? palette.getPrimary() : MaterialTheme.alpha(palette.getOnSurface(), .38f), Fonts.getIconFill(26));
        }
        float textWidth = Math.max(24, width - 256);
        Skia.drawText(Skia.getLimitText(title, Fonts.getMedium(16), textWidth), x + 76, y + 16,
            palette.getOnSurface(), Fonts.getMedium(16));
        Skia.drawText(Skia.getLimitText(subtitle, Fonts.getRegular(12), textWidth), x + 76, y + 41,
            palette.getOnSurfaceVariant(), Fonts.getRegular(12));
        Skia.drawHeightCenteredText(duration, x + width - 168, y + 36,
            palette.getOnSurfaceVariant(), Fonts.getRegular(12));
        float downloadX = x + width - 108;
        if (progress >= 0) {
            Skia.drawFullCenteredText(progress + "%", downloadX + 24, y + 36,
                palette.getPrimary(), Fonts.getMedium(12));
        } else iconButton(downloadX, y + 12, saved ? Icon.DOWNLOAD_DONE : Icon.DOWNLOAD,
            saved, !saved && downloadable, inside(mouseX, mouseY, downloadX, y + 12, 48, 48), palette);
        iconButton(x + width - 56, y + 12, Icon.FAVORITE, liked, likesEnabled,
            inside(mouseX, mouseY, x + width - 56, y + 12, 48, 48), palette);
    }

    public static void button(float x, float y, float width, String label, boolean primary, boolean hovered,
            ColorPalette palette) {
        MaterialControls.button(x, y, width, 48, Skia.getLimitText(label, Fonts.getRegular(16), width - 24), palette, MaterialTheme.opacity(),
            primary ? MaterialControls.ButtonStyle.FILLED : MaterialControls.ButtonStyle.TONAL, hovered ? 1 : 0);
    }

    public static void tooltip(String text, double mouseX, double mouseY, float right, ColorPalette palette) {
        float width = Skia.getTextBounds(text, Fonts.getRegular(12)).getWidth() + 24;
        float x = Math.max(0, Math.min((float) mouseX + 12, right - width));
        float y = (float) mouseY - 40;
        Skia.drawRoundedRect(x, y, width, 30, 8, palette.getInverseSurface());
        Skia.drawHeightCenteredText(text, x + 12, y + 15, palette.getInverseOnSurface(), Fonts.getRegular(12));
    }

    public static boolean inside(double mx, double my, float x, float y, float width, float height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }
}
