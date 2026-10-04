package cn.pupperclient.music;

import cn.pupperclient.management.music.*;
import cn.pupperclient.management.music.lyric.LyricsManager;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

/** Provider contracts exercised through HTTP, legacy disk indexes and actual cache publication. */
final class MusicProviderChecks {
    private static int checks;
    @FunctionalInterface private interface Action { void run() throws Exception; }
    static void run() throws Exception {
        Path root = Files.createTempDirectory("pupper-providers-");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        AtomicInteger permissions = new AtomicInteger(), media = new AtomicInteger(), detailRequests = new AtomicInteger();
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", image);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            require(exchange.getRequestHeaders().getFirst("Cookie") == null, "Credentials sent to Audius");
            String response;
            byte[] bytes;
            if (path.equals("/cover")) {
                require(exchange.getRequestURI().getRawQuery() == null, "NetEase cover parameters leaked to Audius");
                bytes = image.toByteArray();
            } else if (path.contains("/stream") || path.contains("/download")) {
                media.incrementAndGet();
                exchange.getResponseHeaders().set("Location", origin + "/audio");
                exchange.sendResponseHeaders(302, -1); exchange.close(); return;
            } else if (path.equals("/audio")) bytes = "ID3provider-fixture".getBytes(StandardCharsets.UTF_8);
            else {
                String query = exchange.getRequestURI().getRawQuery();
                require(query != null && query.contains("app_name=Pupper%20Client") && !query.contains("cookie"), "API identity or credential isolation broken");
                boolean search = path.equals("/v1/tracks/search");
                if (!search) detailRequests.incrementAndGet();
                if (search) require(query.contains("query=Fixture+%E4%B8%AD%E6%96%87") && query.contains("offset=30"), "Search pagination/Unicode lost");
                response = "{\"data\":" + (search ? "[" : "") + "{\"id\":\"42\",\"title\":\"Fixture Song\",\"duration\":12,"
                    + "\"user\":{\"name\":\"Artist\"},\"artwork\":{\"480x480\":\"" + origin + "/cover\"},"
                    + "\"is_streamable\":true,\"is_stream_gated\":" + (permissions.get() == 1)
                    + ",\"is_downloadable\":true,\"is_download_gated\":false,\"access\":{\"stream\":true,\"download\":"
                    + (permissions.get() != 2) + "}}" + (search ? "]" : "") + "}";
                bytes = response.getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            AudiusMusicProvider audius = new AudiusMusicProvider(URI.create(origin + "/v1/"));
            var search = audius.search("Fixture 中文", 1, 30);
            MusicTrack foreign = search.tracks().getFirst();
            MusicTrack netease = new MusicTrack(42, "Old title", "", "", "", 0);
            require(search.total() == -1 && search.offset() == 30, "Audius invented a result count");
            require(foreign.providerId().equals("42") && foreign.durationMillis() == 12000 && foreign.artist().equals("Artist"), "String IDs or metadata lost");
            require(!foreign.sameSong(netease) && !foreign.coverFilename().equals(netease.coverFilename()), "Provider IDs/covers collide");
            MusicTrack upper = new MusicTrack(0, "Case", "", "", "", 0, "audius", "AbC", true, true);
            MusicTrack lower = new MusicTrack(0, "Case", "", "", "", 0, "audius", "abc", true, true);
            require(!upper.coverFilename().equalsIgnoreCase(lower.coverFilename())
                && !MusicDownload.filename(upper, ".mp3").equalsIgnoreCase(MusicDownload.filename(lower, ".mp3")),
                "Case-sensitive Audius IDs collide on Windows");
            MusicTrack longId = new MusicTrack(0, "😀".repeat(150), "", "", "", 0, "audius", "A".repeat(80), true, true);
            require(MusicDownload.filename(longId, ".mp3").length() <= 240, "Long provider IDs exceed Windows filename limits");
            require(!MusicDownload.filename(foreign, ".mp3").equals(MusicDownload.filename(netease, ".mp3")), "Download filenames collide");
            var limited = AudiusMusicProvider.parse(JsonParser.parseString("{\"id\":\"aB_c\",\"title\":\"Restricted\",\"is_streamable\":true,"
                + "\"is_stream_gated\":true,\"is_downloadable\":true,\"is_download_gated\":true}").getAsJsonObject());
            require(!limited.playable() && !limited.downloadable(), "Gated actions exposed");
            require(AudiusMusicProvider.parse(JsonParser.parseString("{\"id\":\"../unsafe\",\"title\":\"X\"}").getAsJsonObject()) == null, "Unsafe IDs accepted");
            expect("music.error.metadata", () -> audius.track("../unsafe"));
            require(audius.search("Fixture 中文", 2, 30).total() == 31, "Last page not detected");

            Path music = root.resolve("music"); Files.createDirectories(music);
            Files.writeString(music.resolve(".pupper-music.json"), "{\"downloads\":{\"old.mp3\":{\"id\":42,\"title\":\"Old title\"}},"
                + "\"favorites\":{\"guest\":{\"song:42\":{\"track\":{\"id\":42,\"title\":\"Old title\"},\"filename\":\"old.mp3\"}}}}");
            Files.writeString(music.resolve("old.mp3"), "ID3old-fixture");
            MusicLibraryStore store = new MusicLibraryStore(music);
            require(store.metadata("old.mp3").provider().equals("netease") && store.metadata("old.mp3").downloadable(), "Legacy metadata not migrated");
            require(store.isLiked("guest", netease, "") && !store.isLiked("guest", foreign, ""), "Legacy favorite key lost or crossed providers");
            store.setLiked("guest", foreign, "", true);
            require(store.favorites("guest").size() == 2, "Favorite identities collide");
            store.replaceCloudLikes("guest", List.of());
            require(store.isLiked("guest", foreign, "") && !store.isLiked("guest", netease, ""), "NetEase sync deleted Audius favorite");
            store.setLiked("guest", netease, "", true);
            store.setLiked("netease:17", netease, "", true);
            store.setLiked("netease:17", new MusicTrack(0, "Local", "", "", "", 0), "local.mp3", true);
            store.setLiked("netease:other", new MusicTrack(999, "Other account", "", "", "", 0), "", true);
            var combined = store.favorites(java.util.Map.of("netease", "netease:17", "audius", "guest", "local", "netease:17"));
            require(combined.size() == 3 && combined.stream().map(f -> f.track().provider()).collect(java.util.stream.Collectors.toSet())
                .equals(java.util.Set.of("netease", "audius", "local")), "Shared Favorites lost a source, duplicated guest songs or leaked another account");
            MusicProvider neteaseProvider = new NeteaseMusicProvider(new NeteaseMusicApi(URI.create(origin)));
            MusicProviders registry = new MusicProviders(store, neteaseProvider, audius);
            require(registry.selected().id().equals("netease"), "Legacy source changed");
            registry.select("AUDIUS");
            require(new MusicProviders(new MusicLibraryStore(music), neteaseProvider, audius).selected().id().equals("audius"), "Source choice not persisted");
            require(registry.get(netease.provider()).id().equals("netease"), "Source switch rerouted existing tracks");
            require(registry.forReference("netease:42") == neteaseProvider && registry.forReference("42") == audius,
                "Qualified and selected-source command routing differ");
            require(registry.forReference("netease:42").qualities().contains("lossless")
                && registry.forReference("audius:42").qualities().equals(List.of("standard")), "Quality suggestions crossed providers");
            require(neteaseProvider.cloudLikes() && !audius.cloudLikes(), "Unsupported cloud favorites advertised");
            expect("music.error.provider", () -> registry.select("unknown"));
            require(registry.selected() == audius, "Failed source change changed selection");

            Path cache = root.resolve("cache");
            MusicDownload downloader = new MusicDownload(audius, store, music, cache);
            var playback = downloader.playback(foreign, "standard", null, _ -> { });
            require(playback.audio().getParent().equals(cache) && store.downloaded(foreign) == null, "Stream cache became permanent download");
            require(Files.exists(downloader.cover(foreign)) && !Files.exists(downloader.cover(netease)), "Artwork routed to wrong cache");
            int before = media.get(), details = detailRequests.get();
            downloader.playback(foreign, "standard", null, _ -> { });
            require(media.get() == before && detailRequests.get() > details, "Cached playback refetched audio or skipped access validation");
            permissions.set(1);
            expect("music.error.playrestricted", () -> downloader.playback(foreign, "standard", null, _ -> { }));
            permissions.set(2);
            expect("music.error.downloadrestricted", () -> downloader.download(foreign, "standard", "private-NetEase-cookie", null, _ -> { }));
            require(store.downloaded(foreign) == null && media.get() == before, "Restricted operation published/fetched audio");
            permissions.set(0);
            var saved = downloader.download(foreign, "standard", null, null, _ -> { });
            require(saved.audio().getParent().equals(music) && store.downloaded(netease).getFileName().toString().equals("old.mp3"), "Audius download replaced NetEase track");
            require(new MusicLibraryStore(music).downloaded(foreign).equals(saved.audio()), "Audius metadata lost on restart");
            require(store.favorites("guest").stream().filter(f -> f.track().sameSong(foreign)).findFirst().orElseThrow()
                .filename().equals(saved.audio().getFileName().toString()), "Favorite did not resolve to saved track");
            Path legacyLyrics = cache.resolve(netease.lyricsFilename());
            Files.writeString(legacyLyrics, "{\"original\":\"[00:01]NetEase line\",\"translated\":\"\"}");
            var lyrics = new LyricsManager(track -> audius.lyrics(track), cache, Runnable::run);
            Music song = new Music(playback.audio().toFile(), foreign.title(), foreign.artist(), null, Color.BLACK, foreign);
            require(lyrics.get(song).state() == LyricsManager.State.EMPTY, "NetEase lyrics leaked into Audius song");
            Path protectedAudio = cache.resolve("music-preview-old-0.mp3");
            for (int i = 0; i < 10; i++) {
                Path entry = cache.resolve("music-preview-old-" + i + ".mp3");
                Files.writeString(entry, "ID3cache-fixture");
                Files.setLastModifiedTime(entry, java.nio.file.attribute.FileTime.fromMillis(i + 1));
            }
            downloader.playback(foreign, "standard", protectedAudio, _ -> { });
            try (var files = Files.list(cache)) {
                require(files.filter(p -> p.getFileName().toString().startsWith("music-preview-")).count() <= 8,
                    "Temporary audio cache grows without bound");
            }
            require(Files.exists(protectedAudio) && Files.exists(playback.audio()) && Files.exists(legacyLyrics),
                "Cache cleanup removed current playback or unrelated lyrics");
            System.out.println("Music provider checks passed: " + checks + " assertions; Audius HTTP/access, redirects, source selection, legacy migration and namespace isolation.");
        } finally {
            server.stop(0);
            try (var files = Files.walk(root)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }
    private static void expect(String key, Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected " + key); }
        catch (MusicError failure) { require(failure.key().equals(key), "Wrong provider error: " + failure.key()); }
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
