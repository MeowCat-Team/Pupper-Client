package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.management.music.MusicTrack;
import cn.pupperclient.management.music.MusicRepeatMode;
import cn.pupperclient.management.music.lyric.LyricsManager;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.theme.MaterialControls;
import cn.pupperclient.ui.theme.MaterialTheme;
import java.io.File;

/** Stateless player painters, also used by the raster layout preview. */
public final class MusicUi {
    public record Playback(String title, String artist, File cover, boolean playing, MusicRepeatMode repeat, boolean shuffle,
            boolean liked, float volume, float current, float end, boolean enabled) { }
    private MusicUi() { }

    public static void sidebar(String page, String provider, String quality, double mx, double my, ColorPalette palette) {
        Skia.drawFullCenteredText(Icon.MUSIC_NOTE, 40, 44, palette.getPrimary(), Fonts.getIconFill(24));
        Skia.drawText(MusicText.get("music.player.title"), 64, 30, palette.getOnSurface(), Fonts.getMedium(22));
        tab(16, 116, 208, Icon.SEARCH, MusicText.get("music.tab.search"), page.equals("search"),
            inside(mx, my, 16, 116, 208, 48), palette);
        Skia.drawText(MusicText.get("music.sidebar.library"), 28, 192, palette.getOnSurfaceVariant(), Fonts.getMedium(14));
        tab(16, 224, 208, Icon.LIBRARY_MUSIC, MusicText.get("music.tab.library"), page.equals("library"),
            inside(mx, my, 16, 224, 208, 48), palette);
        tab(16, 280, 208, Icon.FAVORITE, MusicText.get("music.tab.liked"), page.equals("liked"),
            inside(mx, my, 16, 280, 208, 48), palette);
        tab(16, 336, 208, Icon.QUEUE_MUSIC, MusicText.get("music.tab.playlists"), page.equals("playlists") || page.equals("playlist"),
            inside(mx, my, 16, 336, 208, 48), palette);
        Skia.drawText(MusicText.get("music.sidebar.sources"), 28, 400, palette.getOnSurfaceVariant(), Fonts.getMedium(14));
        String[] sources = { "netease", "audius" };
        for (int i = 0; i < sources.length; i++) tab(16, 424 + i * 56, 208, Icon.MUSIC_NOTE,
            MusicText.get("music.provider." + sources[i]), provider.equals(sources[i]),
            inside(mx, my, 16, 424 + i * 56, 208, 48), palette);
        tab(16, 548, 208, Icon.GRAPHIC_EQ, Skia.getLimitText(MusicText.get("music.quality.button", MusicText.get("music.quality." + quality)), Fonts.getMedium(14), 120),
            false, inside(mx, my, 16, 548, 208, 48), palette);
        Skia.drawFullCenteredText(Icon.EXPAND_MORE, 200, 572, palette.getOnSurfaceVariant(), Fonts.getIcon(18));
        Skia.drawLine(232, 24, 232, 600, 1, MaterialTheme.alpha(palette.getOutlineVariant(), .4f));
    }

    public static void browserHeader(String page, String subtitle, MusicPlayerLayout.Panel panel,
            double mx, double my, ColorPalette palette) {
        Skia.drawText(Skia.getLimitText(page.equals("playlist") ? subtitle : MusicText.get("music.tab." + page), Fonts.getMedium(28), 728),
            248, 36, palette.getOnSurface(), Fonts.getMedium(28));
        iconButton(992, 24, Icon.LYRICS, panel == MusicPlayerLayout.Panel.LYRICS, true,
            inside(mx, my, 992, 24, 48, 48), palette);
        iconButton(1044, 24, Icon.QUEUE_MUSIC, panel == MusicPlayerLayout.Panel.QUEUE, true,
            inside(mx, my, 1044, 24, 48, 48), palette);
    }

    public static void songRow(float x, float y, float width, File cover, String title, String subtitle, String access,
            String duration, boolean selected, boolean active, boolean playing, boolean liked, boolean playable,
            boolean likesEnabled, int progress, double mx, double my, ColorPalette palette) {
        boolean hovered = inside(mx, my, x, y, width, 64);
        if (selected || hovered) Skia.drawRoundedRect(x, y, width, 64, 12,
            MaterialTheme.alpha(selected ? palette.getSecondaryContainer() : palette.getOnSurface(), selected ? .65f : .06f));
        artwork(cover, x + 12, y + 12, 40, palette);
        if (hovered || active) {
            Skia.drawRoundedRect(x + 12, y + 12, 40, 40, 8, MaterialTheme.alpha(palette.getSurface(), .8f));
            Skia.drawFullCenteredText(active && playing ? Icon.PAUSE : Icon.PLAY_ARROW, x + 32, y + 32,
                MaterialTheme.alpha(palette.getPrimary(), playable ? 1 : .38f), Fonts.getIconFill(24));
        }
        float textWidth = width - 304;
        float badgeWidth = access.isEmpty() ? 0 : Math.min(textWidth - 88, Skia.getTextBounds(access, Fonts.getMedium(14)).getWidth() + 20);
        Skia.drawText(Skia.getLimitText(title, Fonts.getMedium(16), textWidth - (badgeWidth > 0 ? badgeWidth + 8 : 0)), x + 72, y + 13,
            active ? palette.getPrimary() : palette.getOnSurface(), Fonts.getMedium(16));
        if (badgeWidth > 0) {
            float badgeX = x + 72 + textWidth - badgeWidth;
            Skia.drawRoundedRect(badgeX, y + 8, badgeWidth, 24, 8, MaterialTheme.surface(palette.getTertiaryContainer()));
            Skia.drawHeightCenteredText(Skia.getLimitText(access, Fonts.getMedium(14), badgeWidth - 16), badgeX + 8, y + 20,
                palette.getOnTertiaryContainer(), Fonts.getMedium(14));
        }
        Skia.drawText(Skia.getLimitText(subtitle, Fonts.getRegular(14), textWidth), x + 72, y + 37,
            palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        Skia.drawHeightCenteredText(progress >= 0 ? progress + "%" : duration, x + width - 218, y + 32,
            progress >= 0 ? palette.getPrimary() : palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        iconButton(x + width - 160, y + 8, Icon.PLAYLIST_ADD, false, playable,
            inside(mx, my, x + width - 160, y + 8, 48, 48), palette);
        iconButton(x + width - 108, y + 8, Icon.FAVORITE, liked, likesEnabled,
            inside(mx, my, x + width - 108, y + 8, 48, 48), palette);
        iconButton(x + width - 56, y + 8, Icon.MORE_HORIZ, false, true,
            inside(mx, my, x + width - 56, y + 8, 48, 48), palette);
    }

    public static void playlistRow(float x, float y, float width, String name, int count, boolean selected, double mx, double my, ColorPalette palette) {
        if (selected || inside(mx, my, x, y, width, 64)) Skia.drawRoundedRect(x, y, width, 64, 12,
            MaterialTheme.alpha(selected ? palette.getSecondaryContainer() : palette.getOnSurface(), selected ? .65f : .06f));
        Skia.drawRoundedRect(x + 12, y + 8, 48, 48, 12, MaterialTheme.surface(palette.getPrimaryContainer()));
        Skia.drawFullCenteredText(Icon.QUEUE_MUSIC, x + 36, y + 32, palette.getOnPrimaryContainer(), Fonts.getIcon(28));
        Skia.drawText(Skia.getLimitText(name, Fonts.getMedium(18), width - 152), x + 76, y + 10, palette.getOnSurface(), Fonts.getMedium(18));
        Skia.drawText(MusicText.get("music.tracks", count), x + 76, y + 38, palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        iconButton(x + width - 56, y + 8, Icon.MORE_HORIZ, false, true, inside(mx, my, x + width - 56, y + 8, 48, 48), palette);
    }

    public static void queueHeader(float x, float y, float width, MusicTrack track, File cover,
            int count, double mx, double my, ColorPalette palette) {
        Skia.drawText(MusicText.get("music.queue.title"), x + 12, y + 12, palette.getOnSurface(), Fonts.getMedium(22));
        iconButton(x + width - 48, y, Icon.PLAYLIST_REMOVE, false, count > 0,
            inside(mx, my, x + width - 48, y, 48, 48), palette);
        Skia.drawText(MusicText.get("music.nowplaying"), x + 12, y + 58, palette.getOnSurfaceVariant(), Fonts.getMedium(14));
        artwork(cover, x + 12, y + 82, 48, palette);
        Skia.drawText(Skia.getLimitText(track == null ? MusicText.get("music.player.ready") : track.title(), Fonts.getMedium(14), width - 96),
            x + 72, y + 85, palette.getOnSurface(), Fonts.getMedium(14));
        Skia.drawText(Skia.getLimitText(track == null ? "" : MusicText.artist(track), Fonts.getRegular(14), width - 96),
            x + 72, y + 109, palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        Skia.drawText(MusicText.get("music.queue.count", count), x + 12, y + 152, palette.getOnSurfaceVariant(), Fonts.getRegular(14));
    }

    public static void queueRow(float x, float y, float width, MusicTrack track, File cover,
            boolean selected, double mx, double my, ColorPalette palette) {
        if (selected || inside(mx, my, x, y, width, 64)) Skia.drawRoundedRect(x, y, width, 64, 12,
            MaterialTheme.alpha(palette.getSecondaryContainer(), .55f));
        Skia.drawFullCenteredText(Icon.DRAG_INDICATOR, x + 12, y + 32, palette.getOnSurfaceVariant(), Fonts.getIcon(16));
        artwork(cover, x + 24, y + 16, 32, palette);
        Skia.drawText(Skia.getLimitText(track.title(), Fonts.getMedium(15), width - 132), x + 64, y + 14,
            palette.getOnSurface(), Fonts.getMedium(15));
        Skia.drawText(Skia.getLimitText(MusicText.artist(track), Fonts.getRegular(14), width - 132), x + 64, y + 37,
            palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        iconButton(x + width - 48, y + 8, Icon.CLOSE, false, true,
            inside(mx, my, x + width - 48, y + 8, 48, 48), palette);
    }
    public static void queueActions(float x, float y, float width, float height, double mx, double my, ColorPalette palette) {
        button(x, y + height - 48, (width - 8) / 2, MusicText.get("music.queue.add"), false,
            inside(mx, my, x, y + height - 48, (width - 8) / 2, 48), palette);
        button(x + (width + 8) / 2, y + height - 48, (width - 8) / 2, MusicText.get("music.queue.save"), false,
            inside(mx, my, x + (width + 8) / 2, y + height - 48, (width - 8) / 2, 48), palette);
    }

    public static void playback(float x, float y, float width, Playback state, double mouseX, double mouseY,
            ColorPalette palette) {
        MaterialTheme.card(x, y, width, 88, 24, palette);
        artwork(state.cover(), x + 12, y + 12, 48, palette);
        float titleWidth = Math.max(80, Math.min(180, width / 2 - 224));
        Skia.drawText(Skia.getLimitText(state.title(), Fonts.getMedium(14), titleWidth), x + 72, y + 18,
            palette.getOnSurface(), Fonts.getMedium(14));
        Skia.drawText(Skia.getLimitText(state.artist(), Fonts.getRegular(14), titleWidth), x + 72, y + 42,
            palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        iconButton(x + 64 + titleWidth, y + 12, Icon.FAVORITE, state.liked(), state.enabled(),
            inside(mouseX, mouseY, x + 64 + titleWidth, y + 12, 48, 48), palette);
        float center = x + width / 2;
        String[] icons = { state.repeat() == MusicRepeatMode.ONE ? Icon.REPEAT_ONE : Icon.REPEAT, Icon.SKIP_PREVIOUS, state.playing() ? Icon.PAUSE : Icon.PLAY_ARROW,
            Icon.SKIP_NEXT, Icon.SHUFFLE };
        for (int i = 0; i < icons.length; i++) {
            boolean selected = i == 0 && state.repeat() != MusicRepeatMode.OFF || i == 4 && state.shuffle() || i == 2;
            float buttonX = center - 120 + i * 48;
            iconButton(buttonX, y + 4, icons[i], selected, i == 0 || i == 4 || state.enabled(),
                inside(mouseX, mouseY, buttonX, y + 4, 48, 48), palette);
        }
        float progress = state.end() > 0 && Float.isFinite(state.current())
            ? Math.clamp(state.current() / state.end(), 0, 1) : 0;
        float trackWidth = Math.min(256, width / 3);
        float trackX = center - trackWidth / 2;
        Skia.drawRoundedRect(trackX, y + 65, trackWidth, 4, 2, MaterialTheme.surface(palette.getSecondaryContainer()));
        Skia.drawRoundedRect(trackX, y + 65, progress * trackWidth, 4, 2, palette.getPrimary());
        Skia.drawHeightCenteredText(MusicText.time(state.current()), trackX - 42, y + 67,
            palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        Skia.drawHeightCenteredText(MusicText.time(state.end()), trackX + trackWidth + 10, y + 67,
            palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        iconButton(x + width - 164, y + 12, state.volume() == 0 ? Icon.VOLUME_OFF : Icon.VOLUME_UP,
            false, true, inside(mouseX, mouseY, x + width - 164, y + 12, 48, 48), palette);
        MaterialControls.slider(x + width - 108, y + 18, 84, palette, MaterialTheme.opacity(), state.volume(),
            inside(mouseX, mouseY, x + width - 116, y + 8, 104, 60) ? 1 : 0, Math.round(state.volume() * 100) + "%");
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
            Skia.drawCenteredText(Skia.getLimitText(MusicText.get(key), Fonts.getRegular(14), width - 32), x + width / 2, top + bodyHeight / 2 + 30,
                palette.getOnSurfaceVariant(), Fonts.getRegular(14));
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
    }

    public static void nameDialog(String title, String error, double mx, double my, ColorPalette palette) {
        Skia.drawRect(0, 0, MusicPlayerLayout.WIDTH, MusicPlayerLayout.HEIGHT, MaterialTheme.alpha(java.awt.Color.BLACK, .35f));
        Skia.drawRoundedRect(320, 224, 480, 248, 28, MaterialTheme.alpha(java.awt.Color.BLACK, .18f));
        Skia.drawRoundedRect(320, 220, 480, 248, 28, palette.getSurfaceContainerHigh());
        Skia.drawText(title, 344, 248, palette.getOnSurface(), Fonts.getMedium(24));
        Skia.drawText(MusicText.get("music.playlist.name"), 344, 284, palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        if (!error.isEmpty()) Skia.drawText(MusicText.get(error), 344, 361, palette.getError(), Fonts.getRegular(14));
        button(492, 396, 136, MusicText.get("music.action.cancel"), false, inside(mx, my, 492, 396, 136, 48), palette);
        button(640, 396, 136, MusicText.get("music.action.save"), true, inside(mx, my, 640, 396, 136, 48), palette);
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

    public static void button(float x, float y, float width, String label, boolean primary, boolean hovered,
            ColorPalette palette) {
        MaterialControls.button(x, y, width, 48, Skia.getLimitText(label, Fonts.getRegular(16), width - 24), palette, MaterialTheme.opacity(),
            primary ? MaterialControls.ButtonStyle.FILLED : MaterialControls.ButtonStyle.TONAL, hovered ? 1 : 0);
    }

    public static void tooltip(String text, double mouseX, double mouseY, float right, ColorPalette palette) {
        float width = Skia.getTextBounds(text, Fonts.getRegular(14)).getWidth() + 24;
        float x = Math.max(0, Math.min((float) mouseX + 12, right - width));
        float y = (float) mouseY - 40;
        Skia.drawRoundedRect(x, y, width, 30, 8, palette.getInverseSurface());
        Skia.drawHeightCenteredText(text, x + 12, y + 15, palette.getInverseOnSurface(), Fonts.getRegular(14));
    }

    public static boolean inside(double mx, double my, float x, float y, float width, float height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }
}
