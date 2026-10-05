package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.management.music.MusicTrack;
import cn.pupperclient.management.music.MusicRepeatMode;
import cn.pupperclient.management.music.MusicSearchType;
import cn.pupperclient.management.music.MusicCollection;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
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
        String[] pages = { "search", "library", "liked", "playlists", "cloud", "recent", "downloads" };
        String[] icons = { Icon.SEARCH, Icon.LIBRARY_MUSIC, Icon.FAVORITE, Icon.QUEUE_MUSIC, Icon.CLOUD, Icon.HISTORY, Icon.DOWNLOAD };
        for (int i = 0; i < pages.length; i++) {
            var box = MusicPlayerLayout.navBox(pages[i]);
            boolean selected = page.equals(pages[i]) || pages[i].equals("search") && page.equals("browse")
                || pages[i].equals("playlists") && page.equals("playlist");
            tab(box.x(), box.y(), box.width(), icons[i], MusicText.get("music.tab." + pages[i]), selected, box.contains(mx, my), palette);
        }
        Skia.drawText(MusicText.get("music.sidebar.library"), 24, 170, palette.getOnSurfaceVariant(), Fonts.getMedium(12));
        Skia.drawText(MusicText.get("music.sidebar.sources"), 24, 506, palette.getOnSurfaceVariant(), Fonts.getMedium(12));
        for (String source : new String[] { "netease", "audius" }) {
            var box = MusicPlayerLayout.navBox(source);
            tab(box.x(), box.y(), box.width(), Icon.MUSIC_NOTE, MusicText.get("music.provider." + source), provider.equals(source), box.contains(mx, my), palette);
        }
        var settings = MusicPlayerLayout.settingsButton();
        iconButton(settings.x(), settings.y(), Icon.SETTINGS, page.equals("settings"), true, settings.contains(mx, my), palette);
        if (settings.contains(mx, my)) tooltip(MusicText.get("music.tab.settings"), mx, my, MusicPlayerLayout.WIDTH, palette);
        Skia.drawLine(208, 24, 208, MusicPlayerLayout.transport().y() - 16, 1, MaterialTheme.alpha(palette.getOutlineVariant(), .3f));
    }

    public static void browserHeader(String page, String subtitle, MusicPlayerLayout.Panel panel,
            double mx, double my, ColorPalette palette) {
        Skia.drawText(Skia.getLimitText(page.equals("playlist") || page.equals("browse") ? subtitle : MusicText.get("music.tab." + page), Fonts.getMedium(28), MusicPlayerLayout.WIDTH - 264),
            MusicPlayerLayout.content(MusicPlayerLayout.Panel.NONE).x(), 36, palette.getOnSurface(), Fonts.getMedium(28));
    }

    public static void searchTypes(float x, float y, MusicSearchType selected, double mx, double my, ColorPalette palette) {
        for (MusicSearchType type : MusicSearchType.values()) {
            var box = MusicPlayerLayout.searchType(x, y, type.ordinal()); boolean active = type == selected;
            var color = active ? palette.getOnSecondaryContainer() : palette.getOnSurfaceVariant();
            float top = box.y() + 4;
            if (active) Skia.drawRoundedRect(box.x(), top, box.width(), 40, 12, MaterialTheme.surface(palette.getSecondaryContainer()));
            else MaterialTheme.outline(box.x(), top, box.width(), 40, 12, palette, MaterialTheme.opacity());
            if (box.contains(mx, my)) Skia.drawRoundedRect(box.x(), top, box.width(), 40, 12, MaterialTheme.alpha(color, .08f));
            if (active) Skia.drawFullCenteredText(Icon.CHECK, box.x() + 22, top + 20, color, Fonts.getIcon(20));
            Skia.drawFullCenteredText(MusicText.get(type.nameKey()), box.x() + box.width() / 2 + (active ? 8 : 0), top + 20, color, Fonts.getMedium(16));
        }
    }
    public static void collectionRow(float x, float y, float width, MusicCollection collection, File cover,
            boolean selected, double mx, double my, ColorPalette palette) {
        if (selected || inside(mx, my, x, y, width, 64)) Skia.drawRoundedRect(x, y, width, 64, 12,
            MaterialTheme.alpha(selected ? palette.getSecondaryContainer() : palette.getOnSurface(), selected ? .65f : .06f));
        collectionArtwork(collection, cover, x + 12, y + 8, 48, palette);
        Skia.drawText(Skia.getLimitText(collection.name(), Fonts.getMedium(18), width - 140), x + 76, y + 10, palette.getOnSurface(), Fonts.getMedium(18));
        Skia.drawText(Skia.getLimitText(MusicText.collection(collection), Fonts.getRegular(14), width - 140), x + 76, y + 38,
            palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        iconButton(x + width - 56, y + 8, Icon.CHEVRON_RIGHT, false, true, inside(mx, my, x + width - 56, y + 8, 48, 48), palette);
    }
    public static void collectionHeader(float x, float y, float width, MusicCollection collection, File cover, int count,
            boolean enabled, double mx, double my, ColorPalette palette) {
        collectionArtwork(collection, cover, x + 8, y + 52, 64, palette);
        Skia.drawText(Skia.getLimitText(collection.name(), Fonts.getMedium(22), width - 208), x + 88, y + 55,
            palette.getOnSurface(), Fonts.getMedium(22));
        Skia.drawText(Skia.getLimitText(MusicText.collection(collection), Fonts.getRegular(14), width - 208), x + 88, y + 86,
            palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        Skia.drawText(MusicText.get("music.browse.loaded", count), x + 8, y + 134, palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        iconButton(x + width - 104, y + 60, Icon.PLAY_ARROW, false, enabled, inside(mx, my, x + width - 104, y + 60, 48, 48), palette);
        iconButton(x + width - 48, y + 60, Icon.MORE_HORIZ, false, enabled, inside(mx, my, x + width - 48, y + 60, 48, 48), palette);
    }
    private static void collectionArtwork(MusicCollection collection, File cover, float x, float y, float size, ColorPalette palette) {
        if (cover != null && cover.isFile()) Skia.drawRoundedImage(cover, x, y, size, size,
            collection.type() == MusicSearchType.ARTISTS ? size / 2 : size / 5);
        else {
            Skia.drawRoundedRect(x, y, size, size, collection.type() == MusicSearchType.ARTISTS ? size / 2 : size / 5,
                MaterialTheme.surface(palette.getPrimaryContainer()));
            Skia.drawFullCenteredText(collection.type() == MusicSearchType.ARTISTS ? Icon.PERSON : Icon.QUEUE_MUSIC,
                x + size / 2, y + size / 2, palette.getOnPrimaryContainer(), Fonts.getIcon(size * .5f));
        }
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
        playback(x, y, width, state, mouseX, mouseY, palette, false, false);
    }
    public static void playback(float x, float y, float width, Playback state, double mouseX, double mouseY,
            ColorPalette palette, boolean immersive, boolean queueOpen) {
        var bounds = new MusicPlayerLayout.Box(x, y, width, immersive ? 176 : 88);
        if (!immersive) {
            Skia.drawRoundedRect(x, y + 8, width, 72, 12, MaterialTheme.alpha(palette.getSurfaceContainer(), .65f));
            artwork(state.cover(), x + 12, y + 12, 48, palette);
            float titleWidth = Math.max(80, Math.min(180, width / 2 - 224));
            Skia.drawText(Skia.getLimitText(state.title(), Fonts.getMedium(14), titleWidth), x + 72, y + 18,
                palette.getOnSurface(), Fonts.getMedium(14));
            Skia.drawText(Skia.getLimitText(state.artist(), Fonts.getRegular(14), titleWidth), x + 72, y + 42,
                palette.getOnSurfaceVariant(), Fonts.getRegular(14));
            iconButton(x + 64 + titleWidth, y + 12, Icon.FAVORITE, state.liked(), state.enabled(),
                inside(mouseX, mouseY, x + 64 + titleWidth, y + 12, 48, 48), palette);
        }
        String[] icons = { state.repeat() == MusicRepeatMode.ONE ? Icon.REPEAT_ONE : Icon.REPEAT, Icon.SKIP_PREVIOUS, state.playing() ? Icon.PAUSE : Icon.PLAY_ARROW,
            Icon.SKIP_NEXT, Icon.SHUFFLE };
        for (int i = 0; i < icons.length; i++) {
            boolean selected = i == 0 && state.repeat() != MusicRepeatMode.OFF || i == 4 && state.shuffle() || i == 2;
            var button = MusicPlayerLayout.playbackAction(bounds, i, immersive);
            if (i == 2) {
                Skia.drawCircle(button.x() + 24, button.y() + 24, 20, immersive
                    ? MaterialTheme.alpha(palette.getOnSurface(), state.enabled() ? .14f : .06f)
                    : MaterialTheme.alpha(palette.getPrimary(), state.enabled() ? 1 : .38f));
                if (state.enabled() && button.contains(mouseX, mouseY)) Skia.drawCircle(button.x() + 24, button.y() + 24, 20,
                    MaterialTheme.alpha(immersive ? palette.getOnSurface() : palette.getOnPrimary(), .08f));
                Skia.drawFullCenteredText(icons[i], button.x() + 24, button.y() + 24,
                    MaterialTheme.alpha(immersive ? palette.getOnSurface() : palette.getOnPrimary(), state.enabled() ? 1 : .38f),
                    Fonts.getIconFill(26));
            } else iconButton(button.x(), button.y(), icons[i], selected, i == 0 || i == 4 || state.enabled(),
                button.contains(mouseX, mouseY), palette);
        }
        float progress = state.end() > 0 && Float.isFinite(state.current())
            ? Math.clamp(state.current() / state.end(), 0, 1) : 0;
        var seek = MusicPlayerLayout.seekTrack(bounds, immersive);
        float trackWidth = seek.width(), trackX = seek.x();
        Skia.drawRoundedRect(trackX, seek.y(), trackWidth, seek.height(), 2, MaterialTheme.surface(palette.getSecondaryContainer()));
        Skia.drawRoundedRect(trackX, seek.y(), progress * trackWidth, seek.height(), 2, palette.getPrimary());
        if (immersive) {
            Skia.drawText(MusicText.time(state.current()), x, y + 25, palette.getOnSurfaceVariant(), Fonts.getRegular(14));
            String remaining = "−" + MusicText.time(Math.max(0, state.end() - state.current()));
            Skia.drawText(remaining, x + width - Skia.getTextBounds(remaining, Fonts.getRegular(14)).getWidth(), y + 25,
                palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        } else {
            Skia.drawHeightCenteredText(MusicText.time(state.current()), trackX - 42, y + 67,
                palette.getOnSurfaceVariant(), Fonts.getRegular(14));
            Skia.drawHeightCenteredText(MusicText.time(state.end()), trackX + trackWidth + 10, y + 67,
                palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        }
        if (immersive) {
            var like = MusicPlayerLayout.playbackAction(bounds, 5, true);
            iconButton(like.x(), like.y(), Icon.FAVORITE, state.liked(), state.enabled(), like.contains(mouseX, mouseY), palette);
        } else {
            var lyrics = MusicPlayerLayout.playbackAction(bounds, 8, false);
            iconButton(lyrics.x(), lyrics.y(), Icon.LYRICS, false, true, lyrics.contains(mouseX, mouseY), palette);
        }
        var queue = MusicPlayerLayout.playbackAction(bounds, 9, immersive);
        iconButton(queue.x(), queue.y(), Icon.QUEUE_MUSIC, queueOpen, true, queue.contains(mouseX, mouseY), palette);
        var mute = MusicPlayerLayout.playbackAction(bounds, 6, immersive);
        iconButton(mute.x(), mute.y(), state.volume() == 0 ? Icon.VOLUME_OFF : Icon.VOLUME_UP,
            false, true, mute.contains(mouseX, mouseY), palette);
        float volume = Float.isFinite(state.volume()) ? Math.clamp(state.volume(), 0, 1) : 0;
        var volumeTrack = MusicPlayerLayout.volumeTrack(bounds, immersive);
        float volumeX = volumeTrack.x(), volumeY = volumeTrack.y() + 2, volumeWidth = volumeTrack.width();
        float thumbX = volumeX + volume * volumeWidth;
        boolean volumeHovered = MusicPlayerLayout.volumeHit(bounds, immersive).contains(mouseX, mouseY);
        Skia.drawRoundedRect(volumeX, volumeY - 2, volumeWidth, 4, 2,
            MaterialTheme.surface(palette.getSecondaryContainer()));
        if (volume > 0) Skia.drawRoundedRect(volumeX, volumeY - 2, volumeWidth * volume, 4, 2, palette.getPrimary());
        if (volumeHovered) Skia.drawCircle(thumbX, volumeY, 16, MaterialTheme.alpha(palette.getPrimary(), .08f));
        Skia.drawCircle(thumbX, volumeY, 5, palette.getPrimary());
        if (volumeHovered) tooltip(Math.round(volume * 100) + "%", mouseX, mouseY, x + width, palette);
    }

    public static void nameDialog(String title, String error, double mx, double my, ColorPalette palette) {
        var box = MusicPlayerLayout.nameDialog();
        var cancel = MusicPlayerLayout.nameCancel(); var save = MusicPlayerLayout.nameSave();
        Skia.drawRect(0, 0, MusicPlayerLayout.WIDTH, MusicPlayerLayout.HEIGHT, MaterialTheme.alpha(java.awt.Color.BLACK, .35f));
        Skia.drawRoundedRect(box.x(), box.y() + 4, box.width(), box.height(), 28, MaterialTheme.alpha(java.awt.Color.BLACK, .18f));
        Skia.drawRoundedRect(box.x(), box.y(), box.width(), box.height(), 28, palette.getSurfaceContainerHigh());
        Skia.drawText(title, box.x() + 24, box.y() + 28, palette.getOnSurface(), Fonts.getMedium(24));
        Skia.drawText(MusicText.get("music.playlist.name"), box.x() + 24, box.y() + 64, palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        if (!error.isEmpty()) Skia.drawText(MusicText.get(error), box.x() + 24, box.y() + 141, palette.getError(), Fonts.getRegular(14));
        button(cancel.x(), cancel.y(), cancel.width(), MusicText.get("music.action.cancel"), false, cancel.contains(mx, my), palette);
        button(save.x(), save.y(), save.width(), MusicText.get("music.action.save"), true, save.contains(mx, my), palette);
    }

    public static void iconButton(float x, float y, String icon, boolean selected, boolean enabled,
            boolean hovered, ColorPalette palette) {
        if (selected) Skia.drawRoundedRect(x + 8, y + 8, 32, 32, 10,
            MaterialTheme.surface(palette.getSecondaryContainer()));
        if (hovered && enabled) Skia.drawRoundedRect(x + 6, y + 6, 36, 36, 12,
            MaterialTheme.alpha(palette.getOnSurface(), .08f));
        Skia.drawFullCenteredText(icon, x + 24, y + 24,
            MaterialTheme.alpha(selected ? palette.getPrimary() : palette.getOnSurfaceVariant(), enabled ? 1 : .38f),
            selected ? Fonts.getIconFill(22) : Fonts.getIcon(22));
    }

    public static void tab(float x, float y, float width, String icon, String label, boolean selected,
            boolean hovered, ColorPalette palette) {
        if (selected) Skia.drawRoundedRect(x, y + 4, width, 40, 12,
            MaterialTheme.surface(palette.getSecondaryContainer()));
        if (hovered) Skia.drawRoundedRect(x, y + 4, width, 40, 12,
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
        var fill = primary ? palette.getPrimary() : palette.getSecondaryContainer();
        var text = primary ? palette.getOnPrimary() : palette.getOnSecondaryContainer();
        Skia.drawRoundedRect(x, y + 4, width, 40, 12, MaterialTheme.surface(fill));
        if (hovered) Skia.drawRoundedRect(x, y + 4, width, 40, 12, MaterialTheme.alpha(text, .08f));
        Skia.drawFullCenteredText(Skia.getLimitText(label, Fonts.getRegular(16), width - 24), x + width / 2, y + 24, text, Fonts.getRegular(16));
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
