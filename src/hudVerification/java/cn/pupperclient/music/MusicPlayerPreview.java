package cn.pupperclient.music;

import cn.pupperclient.gui.modmenu.component.MusicPlayerLayout;
import cn.pupperclient.gui.modmenu.component.MusicPopupMenu;
import cn.pupperclient.gui.modmenu.component.MusicUi;
import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.management.music.MusicTrack;
import cn.pupperclient.management.music.MusicRepeatMode;
import cn.pupperclient.management.music.lyric.LyricsManager;
import cn.pupperclient.management.music.lyric.SongLyrics;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.context.SkiaContext;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.theme.MaterialControls;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.language.Language;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Offscreen specimens share actual sidebar, song, queue, lyrics and popup painters and geometry. */
public final class MusicPlayerPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]).toAbsolutePath(); Files.createDirectories(output);
        Field field = SkiaContext.class.getDeclaredField("surface"); field.setAccessible(true);
        Object previous = field.get(null);
        try {
            for (boolean dark : new boolean[] { false, true }) {
                I18n.setLanguage(dark ? Language.CHINESE : Language.ENGLISH);
                for (String view : new String[] { "player", "audius", "lyrics", "queue", "menu", "quality", "playlists", "playlist", "playlist-queue", "playlist-create", "playlist-menu" }) {
                    try (Surface surface = Surface.makeRasterN32Premul(1120, 720)) {
                        field.set(null, surface); var canvas = surface.getCanvas();
                        canvas.clear(dark ? 0xff252735 : 0xffe9edf4);
                        var palette = new ColorPalette(Hct.from(265, 42, 60), dark);
                        int count = canvas.getSaveCount();
                        var panel = view.equals("lyrics") ? MusicPlayerLayout.Panel.LYRICS
                            : view.equals("queue") || view.equals("playlist-queue") ? MusicPlayerLayout.Panel.QUEUE : MusicPlayerLayout.Panel.NONE;
                        boolean audius = view.equals("audius");
                        boolean collection = view.equals("playlists") || view.equals("playlist-create");
                        boolean detail = view.startsWith("playlist") && !collection;
                        String page = collection ? "playlists" : detail ? "playlist" : "search";
                        String playlistName = dark ? "夜间散步" : "Evening mix";
                        String source = audius ? "audius" : "netease";
                        var track = new MusicTrack(3356975915L, audius ? "Afterglow" : "Montagem pitty",
                            dark ? "示例歌手" : "Sample artist", "Evening Mix", "", 137000);
                        MaterialTheme.panel(0, 0, 1120, 720, 28, palette);
                        MusicUi.sidebar(page, source, audius ? "standard" : "exhigh", -1, -1, palette);
                        MusicUi.browserHeader(page, playlistName, panel, -1, -1, palette);
                        var body = MusicPlayerLayout.content(panel);
                        String query = collection || detail ? "" : audius ? "Afterglow" : "MONTAGEM PITTY";
                        MaterialControls.textInput(body.x(), body.y(), body.width() - (collection ? 176 : detail ? 112 : 56), 42, true,
                            palette, MaterialTheme.opacity(), 0, 0, new MaterialControls.TextState(query, 0, 0, 0, 0,
                                MusicText.get(collection ? "music.playlist.filter" : detail ? "music.filter.hint" : "music.search.hint"), query.isEmpty() ? 1 : 0));
                        if (collection) MusicUi.button(body.x() + body.width() - 160, body.y() - 3, 160, MusicText.get("music.playlist.create"), true, false, palette);
                        else {
                            MusicUi.iconButton(body.x() + body.width() - 48, body.y() - 3, detail ? Icon.MORE_HORIZ : Icon.SEARCH, false, true, false, palette);
                            if (detail) MusicUi.iconButton(body.x() + body.width() - 104, body.y() - 3, Icon.PLAY_ARROW, false, true, false, palette);
                            Skia.drawText(detail ? MusicText.get("music.tracks", 9) : MusicText.get(audius ? "music.results.query" : "music.results.for",
                                audius ? "Afterglow" : "MONTAGEM PITTY", 9), body.x() + 8, body.y() + 54,
                                palette.getOnSurfaceVariant(), Fonts.getRegular(14));
                        }
                        Skia.save();
                        try {
                            Skia.clip(body.x(), body.y() + 80, body.width(), body.height() - 132, 12);
                            if (collection) {
                                String[] names = { playlistName, dark ? "喜欢的轻音乐" : "Quiet moments", "网易云 + Audius",
                                    dark ? "长歌单名称排版检查".repeat(10) : "Long playlist name for layout review ".repeat(3) };
                                for (int i = 0; i < names.length; i++) MusicUi.playlistRow(body.x(), body.y() + 80 + i * 64,
                                    body.width(), names[i], i == 2 ? 0 : 12 + i, i == 1, -1, -1, palette);
                            } else for (int i = 0; i < 9; i++) MusicUi.songRow(body.x(), body.y() + 80 + i * 64, body.width(), null,
                                i == 0 ? track.title() : i == 1 ? track.title() + " (Slowed)" : track.title() + " · Mix " + i,
                                track.artist() + " · Evening Mix", i == 0 ? "2:17" : "2:42", i == 1, i == 0, true, i == 0,
                                !audius || i != 1, true, i == 2 ? 46 : -1, -1, -1, palette);
                        } finally { Skia.restore(); }
                        var side = MusicPlayerLayout.sidePanel();
                        if (panel != MusicPlayerLayout.Panel.NONE) Skia.drawLine(786, 100, 786, 600, 1,
                            MaterialTheme.alpha(palette.getOutlineVariant(), .4f));
                        if (panel == MusicPlayerLayout.Panel.LYRICS) {
                            var lyrics = SongLyrics.parse("[00:00]A quiet moment\n[00:15]Soft light in the room\n[00:30]Feel the rhythm\n[00:45]Every note can breathe\n[01:00]Stay a little while\n",
                                "[00:00]安静片刻\n[00:15]柔光洒满房间\n[00:30]感受节奏\n[00:45]每个音符都能自由呼吸\n[01:00]在这里多停留一会儿\n");
                            MusicUi.lyrics(side.x(), side.y(), side.width(), side.height(), track.title(),
                                new LyricsManager.Result(LyricsManager.State.READY, lyrics), 2, 2, -1, -1, palette);
                        } else if (panel == MusicPlayerLayout.Panel.QUEUE) {
                            MusicUi.queueHeader(side.x(), side.y(), side.width(), track, null, 7, -1, -1, palette);
                            Skia.save();
                            try {
                                Skia.clip(side.x(), side.y() + 180, side.width(), side.height() - 236, 12);
                                for (int i = 0; i < 7; i++) MusicUi.queueRow(side.x(), side.y() + 180 + i * 64, side.width(),
                                    new MusicTrack(i + 1, "Evening Mix " + (i + 1), track.artist(), "", "", 150000), null, i == 1, -1, -1, palette);
                            } finally { Skia.restore(); }
                            MusicUi.queueActions(side.x(), side.y(), side.width(), side.height(), -1, -1, palette);
                        }
                        MusicUi.playback(16, 616, 1088, new MusicUi.Playback(track.title(), track.artist(), null,
                            true, view.equals("lyrics") ? MusicRepeatMode.ONE : view.equals("player") ? MusicRepeatMode.OFF : MusicRepeatMode.ALL,
                            view.equals("playlist-queue"), true, .65f, 37, 137, true), -1, -1, palette);
                        if (view.equals("menu") || view.equals("quality") || view.equals("playlist-menu")) {
                            MusicPopupMenu menu = new MusicPopupMenu();
                            if (view.equals("menu")) menu.open(1080, 490, List.of(
                                item("music.action.play", Icon.PLAY_ARROW, true), item("music.action.playnext", Icon.PLAYLIST_PLAY, true),
                                item("music.action.playlast", Icon.PLAYLIST_ADD, true), item("music.playlist.add", Icon.LIBRARY_ADD, true), item("music.action.unlike", Icon.FAVORITE, true),
                                item("music.action.saved", Icon.DOWNLOAD, false)));
                            else if (view.equals("playlist-menu")) menu.open(1080, 490, List.of(
                                item("music.action.play", Icon.PLAY_ARROW, true), item("music.action.playnext", Icon.PLAYLIST_PLAY, true),
                                item("music.action.playlast", Icon.PLAYLIST_ADD, true), item("music.playlist.add", Icon.LIBRARY_ADD, true),
                                item("music.action.unlike", Icon.FAVORITE, true), item("music.action.saved", Icon.DOWNLOAD, false),
                                item("music.playlist.moveup", Icon.ARROW_UPWARD, false), item("music.playlist.movedown", Icon.ARROW_DOWNWARD, true),
                                item("music.playlist.remove", Icon.REMOVE, true)));
                            else menu.open(16, 568, cn.pupperclient.management.music.MusicService.QUALITIES.stream().map(q ->
                                new MusicPopupMenu.Item(MusicText.get("music.quality." + q), Icon.GRAPHIC_EQ, true, q.equals("exhigh"), () -> { })).toList());
                            menu.draw(-1, -1, palette);
                        }
                        if (view.equals("playlist-create")) {
                            MusicUi.nameDialog(MusicText.get("music.playlist.create"), "", -1, -1, palette);
                            MaterialControls.textInput(344, 310, 432, MaterialControls.TEXT_HEIGHT, false, palette, MaterialTheme.opacity(), 0, 1,
                                new MaterialControls.TextState(playlistName, 0, 0, 0, playlistName.length(), MusicText.get("music.playlist.name"), 0));
                        }
                        if (canvas.getSaveCount() != count) throw new AssertionError("Player painters leaked canvas state");
                        try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData(EncodedImageFormat.PNG)) {
                            Path file = output.resolve(view + "-" + (dark ? "cn-dark.png" : "en-light.png"));
                            Files.write(file, data.getBytes()); System.out.println("Player production-painter preview: " + file);
                        }
                    }
                }
            }
        } finally { field.set(null, previous); }
    }
    private static MusicPopupMenu.Item item(String key, String icon, boolean enabled) {
        return new MusicPopupMenu.Item(MusicText.get(key), icon, enabled, false, () -> { });
    }
}
