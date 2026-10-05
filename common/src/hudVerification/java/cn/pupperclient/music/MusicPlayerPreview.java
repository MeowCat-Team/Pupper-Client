package cn.pupperclient.music;

import cn.pupperclient.gui.modmenu.component.MusicPlayerLayout;
import cn.pupperclient.gui.modmenu.component.MusicPopupMenu;
import cn.pupperclient.gui.modmenu.component.MusicUi;
import cn.pupperclient.gui.modmenu.component.MusicNowPlayingUi;
import cn.pupperclient.gui.modmenu.component.MusicArtworkPalette;
import cn.pupperclient.gui.modmenu.component.MusicAccountUi;
import cn.pupperclient.gui.modmenu.component.MusicSettingsUi;
import cn.pupperclient.gui.modmenu.component.MusicCloudUi;
import cn.pupperclient.gui.modmenu.component.MusicDownloadsUi;
import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.management.music.MusicTrack;
import cn.pupperclient.management.music.MusicRepeatMode;
import cn.pupperclient.management.music.MusicSearchType;
import cn.pupperclient.management.music.MusicCollection;
import cn.pupperclient.management.music.MusicAccount;
import cn.pupperclient.management.music.MusicDownloadTasks;
import cn.pupperclient.management.music.MusicExperienceStore;
import cn.pupperclient.management.music.NeteaseMusicApi;
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
    private record Specimen(String view, int width, int height, boolean scaled) { }
    private static List<Specimen> specimens() {
        var specimens = new java.util.ArrayList<Specimen>();
        for (String view : new String[] { "player", "audius", "lyrics", "lyrics-long", "lyrics-queue", "lyrics-loading", "lyrics-error", "lyrics-empty", "queue", "menu", "quality",
                "playlists", "playlist", "playlist-queue", "playlist-create", "playlist-menu", "access", "access-queue", "login-qr", "login-phone", "account",
                "search-artists", "search-playlists", "search-artists-queue", "search-playlists-queue", "browse-artist", "browse-playlist", "browse-artist-queue", "browse-playlist-queue",
                "settings", "settings-trusted", "settings-invalid", "cloud-created", "cloud-subscribed", "cloud-guest", "cloud-loading", "cloud-error", "downloads", "downloads-empty" })
            specimens.add(new Specimen(view, (int) MusicPlayerLayout.WIDTH, (int) MusicPlayerLayout.HEIGHT, false));
        for (String view : new String[] { "player", "queue", "lyrics", "lyrics-long", "lyrics-queue", "playlist-create", "login-qr", "login-phone", "account", "settings", "cloud-created", "cloud-guest", "downloads" })
            specimens.add(new Specimen(view, 1366, 768, true));
        specimens.add(new Specimen("player", 1920, 1080, true));
        specimens.add(new Specimen("lyrics-long", 1920, 1080, true));
        return List.copyOf(specimens);
    }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]).toAbsolutePath(); Files.createDirectories(output);
        var selected = args.length < 2 || args[1].isBlank() ? java.util.Set.<String>of()
            : java.util.Set.of(args[1].split(","));
        var cover = artwork(output);
        var tones = MusicArtworkPalette.sample(cover);
        Field field = SkiaContext.class.getDeclaredField("surface"); field.setAccessible(true);
        Object previous = field.get(null);
        try {
            for (boolean dark : new boolean[] { false, true }) {
                I18n.setLanguage(dark ? Language.CHINESE : Language.ENGLISH);
                for (Specimen specimen : specimens()) {
                    String view = specimen.view(); boolean fullscreen = view.startsWith("lyrics");
                    if (!selected.isEmpty() && !selected.contains(view)) continue;
                    try (Surface surface = Surface.makeRasterN32Premul(specimen.width(), specimen.height())) {
                        field.set(null, surface); var canvas = surface.getCanvas();
                        canvas.clear(dark ? 0xff252735 : 0xffe9edf4);
                        var palette = new ColorPalette(Hct.from(265, 42, 60), dark);
                        int count = canvas.getSaveCount();
                        var viewport = specimen.scaled() ? MusicPlayerLayout.fit(specimen.width(), specimen.height(), fullscreen)
                            : new MusicPlayerLayout.Viewport(1, 0, 0, MusicPlayerLayout.WIDTH, MusicPlayerLayout.HEIGHT);
                        var panel = view.equals("queue") || view.endsWith("-queue") ? MusicPlayerLayout.Panel.QUEUE : MusicPlayerLayout.Panel.NONE;
                        boolean audius = view.equals("audius") || view.startsWith("search-artists") || view.startsWith("browse-artist");
                        boolean collection = view.equals("playlists") || view.equals("playlist-create");
                        boolean detail = view.startsWith("playlist") && !collection;
                        boolean remoteDetail = view.startsWith("browse-");
                        var type = view.startsWith("search-artists") || view.startsWith("browse-artist") ? MusicSearchType.ARTISTS
                            : view.startsWith("search-playlists") || remoteDetail ? MusicSearchType.PLAYLISTS : MusicSearchType.SONGS;
                        boolean remoteResults = !remoteDetail && type != MusicSearchType.SONGS;
                        boolean settingsPage = view.startsWith("settings") || view.equals("quality");
                        String page = settingsPage ? "settings" : view.startsWith("cloud") ? "cloud" : view.startsWith("downloads") ? "downloads"
                            : remoteDetail ? "browse" : collection ? "playlists" : detail ? "playlist" : "search";
                        String playlistName = dark ? "夜间散步" : "Evening mix";
                        String source = audius ? "audius" : "netease";
                        var remote = new MusicCollection(source, "77", type == MusicSearchType.SONGS ? MusicSearchType.PLAYLISTS : type,
                            type == MusicSearchType.ARTISTS ? dark ? "示例歌手" : "Sample artist" : playlistName,
                            type == MusicSearchType.ARTISTS ? "@sample_artist" : dark ? "示例创建者" : "Sample curator", "", 98);
                        var track = new MusicTrack(3356975915L, view.equals("lyrics-long")
                            ? dark ? "当夜色轻轻穿过窗边，让音乐陪你走过每一个春夏秋冬" : "Every note can breathe while the city lights keep shining through the night"
                            : audius ? "Afterglow" : "Montagem pitty",
                            dark ? "示例歌手" : "Sample artist", "Evening Mix", "", 137000);
                        if (view.startsWith("access")) track = track.withAccess(1, 30000);
                        var playback = new MusicUi.Playback(track.title(), MusicText.artist(track), cover,
                            true, fullscreen ? MusicRepeatMode.ONE : view.equals("player") ? MusicRepeatMode.OFF : MusicRepeatMode.ALL,
                            view.equals("playlist-queue"), true, .65f, track.preview() ? 17 : 37, track.preview() ? 30 : 137, true);
                        var account = view.equals("account") ? new MusicAccount("preview-session", "123456", dark ? "夜间散步" : "Evening listener", "") : MusicAccount.GUEST;
                        Skia.save();
                        try {
                        Skia.translate(viewport.offsetX(), viewport.offsetY()); Skia.scale(viewport.scale());
                        if (fullscreen) {
                            var playing = MusicPlayerLayout.nowPlaying(viewport.width(), viewport.height());
                            var immersive = MusicNowPlayingUi.immersivePalette(palette);
                            MusicNowPlayingUi.frame(viewport.width(), viewport.height(), playback, -1, -1, immersive, tones);
                            if (panel == MusicPlayerLayout.Panel.QUEUE) queue(playing.lyrics(), track, cover, immersive);
                            else MusicNowPlayingUi.lyrics(playing.lyrics(), track.title(), lyrics(view, dark), 2, 2, -1, -1, immersive);
                            var transport = playing.transport();
                            MusicUi.playback(transport.x(), transport.y(), transport.width(), playback, -1, -1, immersive, true,
                                panel == MusicPlayerLayout.Panel.QUEUE);
                        } else {
                        MaterialTheme.panel(0, 0, MusicPlayerLayout.WIDTH, MusicPlayerLayout.HEIGHT, 28, palette);
                        MusicUi.sidebar(page, source, audius ? "standard" : "exhigh", -1, -1, palette);
                        MusicAccountUi.launcher(MusicPlayerLayout.accountButton(), account, -1, -1, palette);
                        MusicUi.browserHeader(page, remoteDetail ? MusicText.get(type.nameKey()) : playlistName, panel, -1, -1, palette);
                        var body = MusicPlayerLayout.content(panel);
                        if (settingsPage) {
                            var preferences = new MusicExperienceStore.Settings(38, true, -.5f, true, 3, true);
                            String endpoint = view.equals("settings-invalid") ? "http://public.example.invalid/" : NeteaseMusicApi.DEFAULT_ORIGIN.toString();
                            MusicSettingsUi.draw(body, new MusicSettingsUi.State(preferences, endpoint, view.equals("settings-trusted"), "exhigh", "",
                                view.equals("settings-invalid") ? "music.settings.api.invalid" : "", !view.equals("settings") && !view.equals("quality")), -1, -1, palette);
                            if (!view.equals("settings") && !view.equals("quality")) input(MusicSettingsUi.endpoint(body), endpoint, "", palette);
                        } else if (view.startsWith("cloud")) {
                            var tab = view.equals("cloud-subscribed") ? MusicCloudUi.Tab.SUBSCRIBED : MusicCloudUi.Tab.CREATED;
                            var entries = java.util.stream.IntStream.range(0, 8).mapToObj(i -> new MusicCollection("netease", Integer.toString(900 + i),
                                MusicSearchType.PLAYLISTS, i == 0 ? playlistName : i == 1 ? (dark ? "长云歌单名称排版检查" : "A cloud playlist with a long title ").repeat(8)
                                : (dark ? "我的歌单 " : "My playlist ") + i, dark ? "示例创建者" : "Pupper listener", "", 12 + i)).toList();
                            MusicCloudUi.draw(body, new MusicCloudUi.State(tab, view.equals("cloud-guest") || view.equals("cloud-loading") || view.equals("cloud-error") ? List.of() : entries,
                                view.equals("cloud-loading"), !view.equals("cloud-guest") && !view.equals("cloud-loading") && !view.equals("cloud-error"),
                                view.equals("cloud-guest"), view.equals("cloud-error") ? "music.error.network" : ""), 0, -1, -1, palette);
                        } else if (view.startsWith("downloads")) {
                            MusicDownloadsUi.draw(body, view.equals("downloads-empty") ? List.of() : downloadTasks(track), 0, -1, -1, palette);
                        } else {
                        String query = collection || detail || remoteDetail ? "" : remoteResults ? remote.name() : audius ? "Afterglow" : "MONTAGEM PITTY";
                        MaterialControls.textInput(body.x() + (remoteDetail ? 56 : 0), body.y(), body.width() - (collection ? 176 : detail ? 112 : 56), 42, true,
                            palette, MaterialTheme.opacity(), 0, 0, new MaterialControls.TextState(query, 0, 0, 0, 0,
                                MusicText.get(collection ? "music.playlist.filter" : detail || remoteDetail ? "music.filter.hint"
                                    : "music.search.hint." + type.name().toLowerCase(java.util.Locale.ROOT)), query.isEmpty() ? 1 : 0));
                        if (collection) MusicUi.button(body.x() + body.width() - 160, body.y() - 3, 160, MusicText.get("music.playlist.create"), true, false, palette);
                        else if (remoteDetail) {
                            MusicUi.iconButton(body.x(), body.y() - 3, Icon.ARROW_BACK, false, true, false, palette);
                            MusicUi.collectionHeader(body.x(), body.y(), body.width(), remote, null, 30, true, -1, -1, palette);
                        }
                        else {
                            MusicUi.iconButton(body.x() + body.width() - 48, body.y() - 3, detail ? Icon.MORE_HORIZ : Icon.SEARCH, false, true, false, palette);
                            if (detail) MusicUi.iconButton(body.x() + body.width() - 104, body.y() - 3, Icon.PLAY_ARROW, false, true, false, palette);
                            if (!detail) MusicUi.searchTypes(body.x(), body.y(), type, -1, -1, palette);
                            Skia.drawText(detail ? MusicText.get("music.tracks", 9) : MusicText.get(audius ? "music.search.results.query" : "music.search.results.for",
                                query, MusicText.get(type.nameKey()), 9), body.x() + 8, body.y() + MusicPlayerLayout.listOffset(!detail, false) - 26,
                                palette.getOnSurfaceVariant(), Fonts.getRegular(14));
                        }
                        Skia.save();
                        try {
                            float listOffset = MusicPlayerLayout.listOffset(page.equals("search"), remoteDetail);
                            Skia.clip(body.x(), body.y() + listOffset, body.width(), body.height() - listOffset - 52, 12);
                            if (collection) {
                                String[] names = { playlistName, dark ? "喜欢的轻音乐" : "Quiet moments", "网易云 + Audius",
                                    dark ? "长歌单名称排版检查".repeat(10) : "Long playlist name for layout review ".repeat(3) };
                                for (int i = 0; i < names.length; i++) MusicUi.playlistRow(body.x(), body.y() + 80 + i * 64,
                                    body.width(), names[i], i == 2 ? 0 : 12 + i, i == 1, -1, -1, palette);
                            } else if (remoteResults) {
                                for (int i = 0; i < 9; i++) MusicUi.collectionRow(body.x(), body.y() + listOffset + i * 64, body.width(),
                                    new MusicCollection(source, "id" + i, type, i == 0 ? remote.name() : i == 1 ? remote.name() + " · Acoustic"
                                        : (dark ? "长名称排版检查" : "Long collection name for layout review ").repeat(8),
                                        remote.owner(), "", i == 3 ? -1 : i == 4 ? 0 : 98 + i), null, i == 1, -1, -1, palette);
                            } else for (int i = 0; i < 9; i++) MusicUi.songRow(body.x(), body.y() + listOffset + i * 64, body.width(), null,
                                i == 0 ? track.title() : i == 1 ? track.title() + " (Slowed)" : track.title() + " · Mix " + i,
                                track.artist() + " · Evening Mix", view.startsWith("access") ? MusicText.access(track.withAccess(
                                    i == 2 ? 4 : i == 3 ? 8 : i == 4 ? 0 : 1, i == 0 ? 30000 : i == 5 ? -1 : 0)) : "",
                                i == 0 ? "2:17" : "2:42", i == 1, i == 0, true, i == 0,
                                !audius || i != 1, true, i == 2 ? 46 : -1, -1, -1, palette);
                        } finally { Skia.restore(); }
                        if (remoteResults) MusicUi.button(body.x() + body.width() - 136, body.y() + body.height() - 48,
                            136, MusicText.get("music.action.more"), false, false, palette);
                        var side = MusicPlayerLayout.sidePanel();
                        if (panel != MusicPlayerLayout.Panel.NONE) Skia.drawLine(side.x() - 10, side.y(), side.x() - 10, side.y() + side.height(), 1,
                            MaterialTheme.alpha(palette.getOutlineVariant(), .4f));
                        if (panel == MusicPlayerLayout.Panel.QUEUE) {
                            MusicUi.queueHeader(side.x(), side.y(), side.width(), track, null, 7, -1, -1, palette);
                            Skia.save();
                            try {
                                Skia.clip(side.x(), side.y() + 180, side.width(), side.height() - 236, 12);
                                for (int i = 0; i < 7; i++) MusicUi.queueRow(side.x(), side.y() + 180 + i * 64, side.width(),
                                    new MusicTrack(i + 1, "Evening Mix " + (i + 1), track.artist(), "", "", 150000), null, i == 1, -1, -1, palette);
                            } finally { Skia.restore(); }
                            MusicUi.queueActions(side.x(), side.y(), side.width(), side.height(), -1, -1, palette);
                        }
                        }
                        var transport = MusicPlayerLayout.transport();
                        MusicUi.playback(transport.x(), transport.y(), transport.width(), playback, -1, -1, palette, false,
                            panel == MusicPlayerLayout.Panel.QUEUE);
                        if (view.equals("menu") || view.equals("quality") || view.equals("playlist-menu")) {
                            MusicPopupMenu menu = new MusicPopupMenu();
                            if (view.equals("menu")) menu.open(MusicPlayerLayout.WIDTH - 40, 590, List.of(
                                item("music.action.play", Icon.PLAY_ARROW, true), item("music.action.playnext", Icon.PLAYLIST_PLAY, true),
                                item("music.action.playlast", Icon.PLAYLIST_ADD, true), item("music.playlist.add", Icon.LIBRARY_ADD, true), item("music.action.unlike", Icon.FAVORITE, true),
                                item("music.action.saved", Icon.DOWNLOAD, false)));
                            else if (view.equals("playlist-menu")) menu.open(MusicPlayerLayout.WIDTH - 40, 590, List.of(
                                item("music.action.play", Icon.PLAY_ARROW, true), item("music.action.playnext", Icon.PLAYLIST_PLAY, true),
                                item("music.action.playlast", Icon.PLAYLIST_ADD, true), item("music.playlist.add", Icon.LIBRARY_ADD, true),
                                item("music.action.unlike", Icon.FAVORITE, true), item("music.action.saved", Icon.DOWNLOAD, false),
                                item("music.playlist.moveup", Icon.ARROW_UPWARD, false), item("music.playlist.movedown", Icon.ARROW_DOWNWARD, true),
                                item("music.playlist.remove", Icon.REMOVE, true)));
                            else menu.open(MusicSettingsUi.control(body, MusicSettingsUi.Control.QUALITY).x(),
                                MusicSettingsUi.control(body, MusicSettingsUi.Control.QUALITY).y() + 48, cn.pupperclient.management.music.MusicService.QUALITIES.stream().map(q ->
                                new MusicPopupMenu.Item(MusicText.get("music.quality." + q), Icon.GRAPHIC_EQ, true, q.equals("exhigh"), () -> { })).toList());
                            menu.draw(-1, -1, palette);
                        }
                        if (view.equals("playlist-create")) {
                            MusicUi.nameDialog(MusicText.get("music.playlist.create"), "", -1, -1, palette);
                            var input = MusicPlayerLayout.nameInput();
                            MaterialControls.textInput(input.x(), input.y(), input.width(), input.height(), false, palette, MaterialTheme.opacity(), 0, 1,
                                new MaterialControls.TextState(playlistName, 0, 0, 0, playlistName.length(), MusicText.get("music.playlist.name"), 0));
                        }
                        if (view.equals("login-qr") || view.equals("login-phone") || view.equals("account")) {
                            boolean phone = view.equals("login-phone");
                            MusicAccountUi.dialog(new MusicAccountUi.State(phone ? MusicAccountUi.Tab.PHONE : MusicAccountUi.Tab.QR, account,
                                view.equals("account") ? "music.account.status.valid" : phone ? "music.account.code.sent" : "music.account.qr.waiting", "", false, phone ? 45 : 0),
                                null, -1, -1, palette);
                            if (phone) {
                                input(MusicAccountUi.phoneBox(), "13800138000", "", palette);
                                input(MusicAccountUi.captchaBox(), "123456", "", palette);
                            }
                        }
                        }
                        } finally { Skia.restore(); }
                        if (canvas.getSaveCount() != count) throw new AssertionError("Player painters leaked canvas state");
                        try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData(EncodedImageFormat.PNG)) {
                            Path file = output.resolve(view + (specimen.scaled() ? "-" + specimen.width() + "x" + specimen.height() : "")
                                + "-" + (dark ? "cn-dark.png" : "en-light.png"));
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
    private static java.io.File artwork(Path output) throws Exception {
        var image = new java.awt.image.BufferedImage(512, 512, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new java.awt.Color(26, 126, 142)); graphics.fillRect(0, 0, 512, 512);
            graphics.setColor(new java.awt.Color(229, 78, 89)); graphics.fillOval(140, -110, 480, 520);
            graphics.setColor(new java.awt.Color(225, 172, 57)); graphics.fillOval(-120, 290, 520, 330);
            graphics.setColor(new java.awt.Color(83, 70, 159)); graphics.fillOval(240, 270, 330, 300);
            graphics.setColor(new java.awt.Color(248, 229, 208)); graphics.setStroke(new java.awt.BasicStroke(28));
            graphics.drawOval(112, 112, 288, 288);
            graphics.fillOval(212, 212, 88, 88);
        } finally { graphics.dispose(); }
        var file = output.resolve("preview-artwork.png").toFile();
        javax.imageio.ImageIO.write(image, "png", file);
        return file;
    }
    private static void queue(MusicPlayerLayout.Box box, MusicTrack track, java.io.File cover, ColorPalette palette) {
        MusicUi.queueHeader(box.x(), box.y(), box.width(), track, cover, 7, -1, -1, palette);
        Skia.save();
        try {
            Skia.clip(box.x(), box.y() + 180, box.width(), box.height() - 236, 12);
            for (int i = 0; i < 7; i++) MusicUi.queueRow(box.x(), box.y() + 180 + i * 64, box.width(),
                new MusicTrack(i + 1, "Evening Mix " + (i + 1), track.artist(), "", "", 150000), cover, i == 1, -1, -1, palette);
        } finally { Skia.restore(); }
        MusicUi.queueActions(box.x(), box.y(), box.width(), box.height(), -1, -1, palette);
    }
    private static List<MusicDownloadTasks.Task> downloadTasks(MusicTrack track) {
        return List.of(new MusicDownloadTasks.Task(1, track, "exhigh", MusicDownloadTasks.State.RUNNING, 67, "", null, 1),
            new MusicDownloadTasks.Task(2, new MusicTrack(2, "A queued song with a long title for layout review ".repeat(3), "Sample artist", "", "", 120000),
                "standard", MusicDownloadTasks.State.QUEUED, 0, "", null, 0),
            new MusicDownloadTasks.Task(3, track, "standard", MusicDownloadTasks.State.FAILED, 42, "music.error.network", null, 1),
            new MusicDownloadTasks.Task(4, track, "standard", MusicDownloadTasks.State.CANCELLED, 12, "music.downloads.cancelled", null, 1),
            new MusicDownloadTasks.Task(5, track.withAccess(1, 30000), "exhigh", MusicDownloadTasks.State.COMPLETED, 100, "", Path.of("preview.mp3"), 1),
            new MusicDownloadTasks.Task(6, track, "exhigh", MusicDownloadTasks.State.COMPLETED, 100, "", Path.of("complete.mp3"), 1));
    }
    private static void input(MusicPlayerLayout.Box box, String value, String hint, ColorPalette palette) {
        MaterialControls.textInput(box.x(), box.y(), box.width(), box.height(), false, palette, MaterialTheme.opacity(), 0, 0,
            new MaterialControls.TextState(value, 0, 0, value.length(), value.length(), hint, value.isEmpty() ? 1 : 0));
    }
    private static LyricsManager.Result lyrics(String view, boolean chinese) {
        if (view.equals("lyrics-loading")) return new LyricsManager.Result(LyricsManager.State.LOADING, SongLyrics.EMPTY);
        if (view.equals("lyrics-error")) return new LyricsManager.Result(LyricsManager.State.ERROR, SongLyrics.EMPTY);
        if (view.equals("lyrics-empty")) return new LyricsManager.Result(LyricsManager.State.EMPTY, SongLyrics.EMPTY);
        String active = view.equals("lyrics-long") ? chinese
            ? "当夜色轻轻穿过窗边每个音符都能自由呼吸让我们把这一刻的温柔留在漫长的旅途中".repeat(2)
            : "Every note can breathe while the city lights keep shining through the night and we carry this quiet moment wherever the road takes us"
            : chinese ? "感受节奏，每个音符都能自由呼吸" : "Feel the rhythm, every note can breathe";
        var lyrics = SongLyrics.parse("[00:00]A quiet moment\n[00:15]Soft light in the room\n[00:30]" + active + "\n[00:45]Every note can breathe\n[01:00]Stay a little while\n",
            "[00:00]安静片刻\n[00:15]柔光洒满房间\n[00:30]感受节奏，让音乐陪伴每一个安静的瞬间\n[00:45]每个音符都能自由呼吸\n[01:00]在这里多停留一会儿\n");
        return new LyricsManager.Result(LyricsManager.State.READY, lyrics);
    }
}
