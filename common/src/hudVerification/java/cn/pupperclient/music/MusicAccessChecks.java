package cn.pupperclient.music;

import cn.pupperclient.management.music.*;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.language.Language;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Catalogue fees and actual audio previews must survive downloads without contaminating full-song caches. */
final class MusicAccessChecks {
    private enum Access { PREVIEW, FULL, UNKNOWN, SHIFTED, TEXT_NULL, SHORT_FULL }
    private static int checks;
    static void run() throws Exception {
        var parsed = NeteaseMusicApi.parseTracks(JsonParser.parseString("["
            + "{\"id\":1,\"name\":\"VIP\",\"fee\":1},{\"id\":2,\"name\":\"Album\",\"fee\":4},"
            + "{\"id\":3,\"name\":\"Download\",\"fee\":8},{\"id\":4,\"name\":\"Free\"},"
            + "{\"id\":5,\"name\":\"Nested\",\"fee\":0,\"privilege\":{\"fee\":1}}]").getAsJsonArray());
        require(parsed.stream().map(MusicTrack::fee).toList().equals(List.of(1, 4, 8, 0, 1)), "Catalogue/nested fee flags lost");
        require(parsed.stream().allMatch(track -> track.playable() && track.downloadable() && !track.preview()),
            "Catalogue fees guessed account privileges or marked every VIP song as a preview");
        var audius = new MusicTrack(0, "Foreign", "", "", "", 0, "audius", "id", true, true).withAccess(1, 0);
        require(audius.fee() == 0, "NetEase VIP fees leaked into Audius metadata");
        Path root = Files.createTempDirectory("pupper-access-checks-");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        AtomicReference<Access> mode = new AtomicReference<>(Access.PREVIEW);
        AtomicInteger media = new AtomicInteger();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath(); Access current = mode.get();
            String trial = switch (current) {
                case PREVIEW -> "{\"start\":0,\"end\":30}";
                case SHIFTED -> "{\"start\":60,\"end\":90}";
                case UNKNOWN -> "{}";
                case TEXT_NULL -> "\"null\"";
                default -> "null";
            };
            String response = switch (path) {
                case "/song/detail" -> "{\"code\":200,\"songs\":[{\"id\":77,\"name\":\"VIP fixture\",\"fee\":0,"
                    + "\"ar\":[{\"name\":\"Artist\"}],\"dt\":120000}],\"privileges\":[{\"id\":77,\"fee\":1}]}";
                case "/song/url/v1" -> "{\"code\":200,\"data\":[{\"id\":77,\"url\":\"" + origin
                    + "/media\",\"type\":\"mp3\",\"fee\":1,\"freeTrialInfo\":" + trial
                    + (current == Access.SHORT_FULL ? ",\"time\":30000" : "") + "}]}";
                case "/media" -> { media.incrementAndGet(); yield "ID3" + current.name(); }
                default -> "{}";
            };
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        Language previousLanguage = I18n.getCurrentLanguage();
        try {
            var api = new NeteaseMusicApi(URI.create(origin));
            var provider = new NeteaseMusicProvider(api);
            MusicTrack vip = provider.track("77");
            require(vip.fee() == 1 && !vip.preview(), "Detail privileges were not matched to their song ID");
            var source = provider.audio(vip, "exhigh", null, false);
            require(source.fee() == 1 && source.previewMillis() == 30000, "30-second trial response lost its actual duration");
            Path music = root.resolve("music"), cache = root.resolve("cache");
            var store = new MusicLibraryStore(music);
            var download = new MusicDownload(provider, store, music, cache);
            var preview = download.playback(vip, "exhigh", null, null, _ -> {});
            require(preview.track().preview() && preview.track().previewSeconds() == 30 && preview.track().durationMillis() == 120000,
                "Preview flag was lost or catalogue duration was overwritten");
            require(store.downloaded(vip) == null, "Playing a preview silently downloaded it into the library");
            mode.set(Access.FULL);
            var full = download.playback(vip, "exhigh", "fixture-vip-cookie", preview.audio(), _ -> {});
            require(!full.track().preview() && full.track().fee() == 1 && !full.audio().equals(preview.audio()),
                "Authorized full playback reused a preview cache or lost the catalogue VIP badge");
            require(Files.readString(full.audio()).equals("ID3FULL") && Files.readString(preview.audio()).equals("ID3PREVIEW"),
                "Full/preview cache publication changed the wrong audio bytes");
            mode.set(Access.PREVIEW);
            require(download.playback(vip, "exhigh", null, full.audio(), _ -> {}).audio().equals(preview.audio()),
                "An account without full playback reused another account's full-song cache");
            var playlist = store.createPlaylist("Access", List.of(new MusicQueue.Entry(vip, "")));
            store.setLiked("guest", vip, "", true);
            var saved = download.download(vip, "exhigh", null, null, _ -> {});
            require(saved.track().preview() && new MusicLibraryStore(music).metadata(saved.audio().getFileName().toString()).preview(),
                "Saved preview appeared as a full song after restart");
            require(store.playlist(playlist.id()).entries().getFirst().track().preview()
                && store.favorites("guest").getFirst().track().preview(), "Playlist/favorite metadata discarded the preview label");
            store.refreshMetadata(saved.audio().getFileName().toString(), vip.withAccess(8, 0));
            require(store.metadata(saved.audio().getFileName().toString()).fee() == 8 && store.metadata(saved.audio().getFileName().toString()).preview(),
                "Catalogue refresh erased a known saved preview while enriching fee metadata");
            int before = media.get();
            require(download.download(vip, "exhigh", null, null, _ -> {}).track().preview() && media.get() == before,
                "Rechecking an unchanged saved preview redownloaded bytes or erased its label");
            mode.set(Access.FULL);
            expect("music.error.previewplaying", () -> download.download(vip, "exhigh", "fixture-vip-cookie", saved.audio(), _ -> {}));
            require(Files.readString(saved.audio()).equals("ID3PREVIEW") && store.metadata(saved.audio().getFileName().toString()).preview(),
                "Upgrade damaged a playing preview or its metadata");
            var upgraded = download.download(vip, "exhigh", "fixture-vip-cookie", null, _ -> {});
            require(upgraded.audio().equals(saved.audio()) && Files.readString(upgraded.audio()).equals("ID3FULL") && !upgraded.track().preview(),
                "Full-song upgrade left a stale preview file or label");
            require(!new MusicLibraryStore(music).playlist(playlist.id()).entries().getFirst().track().preview(),
                "Full-song upgrade did not repair persisted playlist metadata");
            store.refreshMetadata(upgraded.audio().getFileName().toString(), preview.track());
            require(!store.metadata(upgraded.audio().getFileName().toString()).preview(), "An outdated refresh snapshot restored the preview flag after a full upgrade");
            var stale = new MusicTrack(vip.id(), vip.title(), vip.artist(), vip.album(), origin + "/cover", vip.durationMillis(),
                vip.provider(), vip.providerId(), true, true, vip.fee(), 30000);
            require(!download.download(stale, "exhigh", null, null, _ -> {}).track().preview()
                && !store.metadata(upgraded.audio().getFileName().toString()).preview(), "Reusing full audio adopted a stale search preview flag");

            mode.set(Access.SHIFTED);
            require(api.audio(77, "standard", null).previewMillis() == 30000, "Trial end timestamp was treated as duration");
            mode.set(Access.UNKNOWN);
            var unknown = vip.withAccess(1, api.audio(77, "standard", null).previewMillis());
            require(unknown.preview() && unknown.previewSeconds() == 0, "Missing preview duration was invented or treated as full audio");
            mode.set(Access.TEXT_NULL);
            require(api.audio(77, "standard", null).previewMillis() == 0, "Text null became a preview marker");
            mode.set(Access.SHORT_FULL);
            require(api.audio(77, "standard", null).previewMillis() == 0, "An actual 30-second song was guessed to be a VIP preview");
            for (Language language : new Language[]{Language.ENGLISH, Language.CHINESE}) {
                I18n.setLanguage(language);
                require(MusicText.access(vip).equals("VIP") && MusicText.access(preview.track()).contains("30"), "VIP/preview translation lost its badge or duration");
                require(!MusicText.access(unknown).contains("30") && !MusicText.access(unknown).contains("music."), "Unknown trial duration was mislabeled or untranslated");
                require(MusicText.downloaded(preview.track()).contains("30") && !MusicText.downloaded(preview.track()).equals(MusicText.downloaded(vip)),
                    "Preview download success is indistinguishable from a full download");
                require(!MusicText.downloadAction(preview.track()).equals(MusicText.downloadAction(vip)), "Preview download menu has no translated distinction");
            }

            Path failedDirectory = root.resolve("failed"); var failedStore = new MusicLibraryStore(failedDirectory);
            var failedDownload = new MusicDownload(provider, failedStore, failedDirectory, root.resolve("failed-cache"));
            mode.set(Access.PREVIEW);
            var original = failedDownload.download(vip, "exhigh", null, null, _ -> {});
            Files.delete(failedDirectory.resolve(".pupper-music.json"));
            Files.createDirectory(failedDirectory.resolve(".pupper-music.json"));
            Files.writeString(failedDirectory.resolve(".pupper-music.json/marker"), "fixture");
            mode.set(Access.FULL);
            try { failedDownload.download(vip, "exhigh", "fixture-vip-cookie", null, _ -> {}); throw new AssertionError("Expected failed upgrade index"); }
            catch (IOException expected) {
                require(Files.readString(original.audio()).equals("ID3PREVIEW") && failedStore.metadata(original.audio().getFileName().toString()).preview(),
                    "Failed index save lost the original preview bytes or published upgraded state");
            }
            try (var files = Files.list(failedDirectory)) {
                require(files.noneMatch(file -> file.toString().endsWith(".part") || file.getFileName().toString().startsWith(".music-replaced-")),
                    "Upgrade rollback leaked a partial or backup file");
            }
            System.out.println("Music access checks passed: " + checks + " assertions; VIP fees, preview duration, full/preview cache isolation, restart labels, authorized upgrade and rollback.");
        } finally {
            server.stop(0);
            if (previousLanguage != null) I18n.setLanguage(previousLanguage);
            try (var files = Files.walk(root)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void expect(String key, Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected " + key); }
        catch (MusicError failure) { require(failure.key().equals(key), "Wrong preview error: " + failure.key()); }
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
