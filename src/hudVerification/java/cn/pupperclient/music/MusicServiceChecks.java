package cn.pupperclient.music;

import cn.pupperclient.management.music.MusicDownload;
import cn.pupperclient.management.music.MusicError;
import cn.pupperclient.management.music.MusicLibraryStore;
import cn.pupperclient.management.music.MusicTrack;
import cn.pupperclient.management.music.NeteaseMusicApi;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Real HTTP and filesystem fixtures; no Minecraft, public audio or live account mutations. */
public final class MusicServiceChecks {
    private static int checks;
    private static volatile boolean detailsFail, cloudFail, badAudio, unavailable, flac;
    private static volatile int mediaRequests;
    private static volatile Map<String, String> lastSearch = Map.of(), lastLike = Map.of(), lastAudio = Map.of();
    private static String origin;
    private static final String COOKIE = "test-session=fixture; os=pc";

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("pupper-music-checks-");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", MusicServiceChecks::respond);
        server.start();
        try {
            NeteaseMusicApi api = new NeteaseMusicApi(URI.create(origin));
            require(api.lyrics(3356975915L).original().contains("Fixture lyric"), "Provider lyric schema unsupported");
            Path libraryDir = root.resolve("music"), cache = root.resolve("cache");
            MusicLibraryStore store = new MusicLibraryStore(libraryDir);
            MusicDownload downloader = new MusicDownload(api, store, libraryDir, cache);
            var result = api.search("MONTAGEM PITTY 中文", 30, 30);
            require(result.offset() == 30 && result.total() == 61, "Search pagination lost");
            require(lastSearch.get("keywords").equals("MONTAGEM PITTY 中文"), "Unicode/multiword query lost");
            require(lastSearch.get("offset").equals("30"), "Offset not sent");
            MusicTrack track = result.tracks().getFirst();
            require(track.title().equals("Montagem pitty") && track.artist().equals("Artist One, 歌手二"), "Cloud metadata lost");
            cloudFail = true;
            var fallback = api.search("MONTAGEM PITTY", 1, 0);
            require(fallback.tracks().getFirst().artist().equals(track.artist()), "Legacy search artist schema unsupported");
            cloudFail = false;
            detailsFail = true;
            List<Integer> progress = new ArrayList<>();
            var downloaded = downloader.download(track, "exhigh", null, null, progress::add);
            require(downloaded.audio().getFileName().toString().startsWith("Montagem pitty - Artist One"),
                "Detail outage regressed a known title to music_ID");
            require(downloaded.audio().getFileName().toString().endsWith(".mp3"), "MP3 format lost");
            require(progress.getLast() == 100, "Download progress not complete");
            require(store.metadata(downloaded.audio().getFileName().toString()).title().equals(track.title()),
                "Provider title not persisted");
            require(new MusicLibraryStore(libraryDir).downloaded(track.id()).equals(downloaded.audio()), "Restart lost download index");
            MusicTrack previewTrack = new MusicTrack(77, "Listen before saving", "Artist", "", "", 137000);
            var preview = downloader.playback(previewTrack, "standard", COOKIE, null, _ -> { });
            require(preview.audio().getParent().equals(cache) && store.downloaded(previewTrack) == null,
                "NetEase listening added an unsolicited library download");
            require(COOKIE.equals(lastAudio.get("cookie")) && "standard".equals(lastAudio.get("level")),
                "NetEase temporary playback lost authentication or selected quality");
            var higherPreview = downloader.playback(previewTrack, "exhigh", COOKIE, preview.audio(), _ -> { });
            require(!higherPreview.audio().equals(preview.audio()) && Files.exists(preview.audio()),
                "A quality change reused different-quality audio or removed playing audio");
            int before = mediaRequests;
            downloader.download(track, "exhigh", null, null, _ -> { });
            require(mediaRequests == before, "Downloaded track fetched twice");

            MusicTrack legacy = new MusicTrack(44, "恢复歌曲名称", "歌手", "专辑", "", 30_000);
            Path legacyFile = libraryDir.resolve("music_44.mp3");
            Files.write(legacyFile, "ID3fixture".getBytes(StandardCharsets.UTF_8));
            store.setLiked("guest", new MusicTrack(0, "music_44", "", "", "", 0), "music_44.mp3", true);
            var playing = downloader.download(legacy, "exhigh", null, legacyFile, _ -> { });
            require(playing.audio().equals(legacyFile), "Actively playing file renamed");
            require(store.metadata("music_44.mp3").title().equals("恢复歌曲名称"), "Playing title not repaired");
            require(store.isLiked("guest", legacy, "music_44.mp3"), "Legacy metadata repair lost the favorite");
            var repaired = downloader.download(legacy, "exhigh", null, null, _ -> { });
            require(!Files.exists(legacyFile) && repaired.audio().getFileName().toString().startsWith("恢复歌曲名称"),
                "Idle legacy file not renamed");
            require(store.metadata("music_44.mp3") == null, "Stale metadata filename retained");
            require(mediaRequests == before, "Legacy repair downloaded existing audio");
            require(store.favorites("guest").getFirst().filename().equals(repaired.audio().getFileName().toString()),
                "Renaming lost the favorite filename");

            flac = true;
            var lossless = downloader.download(new MusicTrack(45, "Lossless", "", "", "", 0), "lossless", null, null, _ -> { });
            require(lossless.audio().getFileName().toString().endsWith(".flac"), "FLAC mislabeled as MP3");
            flac = false;
            badAudio = true;
            expectError("music.error.format", () -> downloader.download(new MusicTrack(46, "Bad", "", "", "", 0),
                "exhigh", null, null, _ -> { }));
            require(store.downloaded(46) == null, "Failed download entered library");
            try (var files = Files.list(libraryDir)) {
                require(files.noneMatch(p -> p.toString().endsWith(".part")), "Partial download not cleaned up");
            }
            badAudio = false;
            unavailable = true;
            expectError("music.error.unavailable", () -> api.audio(99, "exhigh", null));
            unavailable = false;
            detailsFail = false;

            api.like(track.id(), true, COOKIE);
            require(lastLike.get("cookie").equals(COOKIE) && lastLike.get("like").equals("true"), "Like auth/body mismatch");
            api.like(track.id(), false, COOKIE);
            require(lastLike.get("like").equals("false"), "Unlike not sent");
            require(api.likes("17", COOKIE).equals(List.of(track.id())), "Cloud liked IDs lost");
            expectError("music.error.login", () -> api.like(track.id(), true, null));
            store.setLiked("guest", track, "", true);
            require(store.isLiked("guest", track, ""), "Guest like not saved");
            require(!store.isLiked("netease:17", track, ""), "Guest like leaked into another account");
            store.replaceCloudLikes("netease:17", List.of(track));
            require(new MusicLibraryStore(libraryDir).favorites("netease:17").size() == 1, "Cloud favorite not persisted");
            store.replaceCloudLikes("netease:17", List.of());
            require(!store.isLiked("netease:17", track, "") && store.isLiked("guest", track, ""),
                "Sync did not remove cloud unlikes or changed guest favorites");
            MusicTrack local = new MusicTrack(0, "Local song", "", "", "", 0);
            store.setLiked("guest", local, "local.mp3", true);
            require(store.isLiked("guest", local, "local.mp3") && !store.isLiked("guest", local, "other.mp3"),
                "Local favorites have no stable file identity");
            String filename = MusicDownload.filename(new MusicTrack(9, "../../A:*?中文", "name\\test", "", "", 0), ".mp3");
            require(!filename.matches(".*[\\\\/:*?\"<>|].*"), "Unsafe filename survived sanitization");
            require(libraryDir.resolve(filename).normalize().getParent().equals(libraryDir), "Filename escaped music directory");
            Path failedDir = root.resolve("unwritable-index");
            MusicLibraryStore failedStore = new MusicLibraryStore(failedDir);
            Files.createDirectory(failedDir.resolve(".pupper-music.json"));
            Files.writeString(failedDir.resolve(".pupper-music.json/marker"), "fixture");
            try { failedStore.setLiked("guest", track, "", true); throw new AssertionError("Expected a file error"); }
            catch (IOException expected) { require(!failedStore.isLiked("guest", track, ""), "Failed save changed favorite state"); }
            System.out.println("Music HTTP/filesystem checks passed: " + checks + " assertions; search fallback, Unicode queries, "
                + "title persistence, legacy repair, MP3/FLAC, partial cleanup and account-isolated favorites.");
            MusicPlaybackChecks.run();
            MusicLyricsChecks.run();
            MusicProviderChecks.run();
            MusicInteractionChecks.run();
            MusicPlaylistChecks.run();
            MusicAccessChecks.run();
        } finally {
            server.stop(0);
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        Map<String, String> params = decode(exchange.getRequestMethod().equals("POST")
            ? new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8) : exchange.getRequestURI().getRawQuery());
        String track = "{\"id\":3356975915,\"name\":\"Montagem pitty\",\"ar\":[{\"name\":\"Artist One\"},{\"name\":\"歌手二\"}],"
            + "\"al\":{\"name\":\"Pitty\",\"picUrl\":\"\"},\"dt\":120000}";
        byte[] bytes;
        int status = 200;
        switch (path) {
            case "/cloudsearch", "/search" -> {
                lastSearch = params;
                if (path.equals("/cloudsearch") && cloudFail) { status = 404; bytes = "{}".getBytes(StandardCharsets.UTF_8); }
                else {
                    if (path.equals("/search")) track = track.replace("\"ar\"", "\"artists\"").replace("\"al\"", "\"album\"");
                    bytes = ("{\"code\":200,\"result\":{\"songCount\":61,\"songs\":[" + track + "]}}").getBytes(StandardCharsets.UTF_8);
                }
            }
            case "/song/detail" -> {
                if (detailsFail) { status = 503; bytes = "{}".getBytes(StandardCharsets.UTF_8); }
                else bytes = ("{\"code\":200,\"songs\":[" + track + "]}").getBytes(StandardCharsets.UTF_8);
            }
            case "/song/url/v1" -> { lastAudio = params; bytes = ("{\"code\":200,\"data\":[{\"url\":" + (unavailable ? "null" : "\"" + origin + "/media\"")
                + ",\"type\":\"" + (flac ? "flac" : "mp3") + "\"}]}").getBytes(StandardCharsets.UTF_8); }
            case "/lyric" -> bytes = "{\"code\":200,\"lrc\":{\"lyric\":\"[00:01.00]Fixture lyric\"},\"tlyric\":{\"lyric\":\"[00:01.00]测试歌词\"}}".getBytes(StandardCharsets.UTF_8);
            case "/media" -> { mediaRequests++; bytes = (badAudio ? "<html>not audio" : flac ? "fLaCfixture" : "ID3fixture").getBytes(StandardCharsets.UTF_8); }
            case "/like", "/likelist" -> {
                if (!exchange.getRequestMethod().equals("POST") || exchange.getRequestURI().getRawQuery() != null)
                    throw new IOException("Cookie must be in a POST body");
                lastLike = params;
                if (!COOKIE.equals(params.get("cookie"))) { status = 401; bytes = "{}".getBytes(StandardCharsets.UTF_8); }
                else bytes = "{\"code\":200,\"ids\":[3356975915]}".getBytes(StandardCharsets.UTF_8);
            }
            default -> { status = 404; bytes = "{}".getBytes(StandardCharsets.UTF_8); }
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static Map<String, String> decode(String form) {
        Map<String, String> result = new HashMap<>();
        if (form == null) return result;
        for (String entry : form.split("&")) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2) result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return result;
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static void expectError(String key, Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected " + key); }
        catch (MusicError error) { require(error.key().equals(key), "Wrong error: " + error.key()); }
    }
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
