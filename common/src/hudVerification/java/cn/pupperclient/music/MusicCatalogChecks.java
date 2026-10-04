package cn.pupperclient.music;

import cn.pupperclient.gui.modmenu.component.MusicPlayerLayout;
import cn.pupperclient.management.music.*;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.language.Language;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

/** Exercises typed discovery, actual endpoint pagination and stale search/browse replies without Minecraft. */
final class MusicCatalogChecks {
    private static int checks;
    static void run() throws Exception {
        var root = Files.createTempDirectory("pupper-catalog-");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        var fallback = new AtomicBoolean(); AtomicInteger playlistCalls = new AtomicInteger(), authenticated = new AtomicInteger();
        var image = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", image);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath(), query = exchange.getRequestURI().getRawQuery();
            var parameters = params(query);
            if (exchange.getRequestMethod().equals("POST")) {
                parameters = params(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                require(!queryContains(query, "cookie"), "NetEase collection cookie appeared in the URL");
                require(parameters.get("cookie").equals("fixture-cookie"), "NetEase collection request lost its account"); authenticated.incrementAndGet();
            }
            if (path.equals("/cloudsearch") && fallback.get()) { exchange.sendResponseHeaders(500, -1); exchange.close(); return; }
            int offset = Integer.parseInt(parameters.getOrDefault("offset", "0")), limit = Integer.parseInt(parameters.getOrDefault("limit", "30"));
            String response;
            if (path.equals("/cover")) {
                byte[] bytes = image.toByteArray(); exchange.sendResponseHeaders(200, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); } return;
            } else if (path.equals("/cloudsearch") || path.equals("/search")) {
                require(parameters.get("keywords").equals("Night 中文"), "Typed NetEase query lost Unicode");
                String data = switch (parameters.get("type")) {
                    case "1" -> "\"songs\":[" + neteaseSong(101) + "],\"songCount\":51";
                    case "100" -> "\"artists\":[{\"id\":77,\"name\":\"Artist 中文\",\"musicSize\":3,\"img1v1Url\":\"" + origin + "/cover\"},{}],\"artistCount\":51";
                    case "1000" -> "\"playlists\":[{\"id\":77,\"name\":\"Night 中文\",\"trackCount\":3,\"creator\":{\"nickname\":\"Curator\"},\"coverImgUrl\":\"" + origin + "/cover\"},{}],\"playlistCount\":51";
                    default -> throw new AssertionError("Wrong search type");
                };
                response = "{\"code\":200,\"result\":{" + data + "}}";
            } else if (path.equals("/artist/songs") || path.equals("/playlist/track/all")) {
                require(parameters.get("id").equals("77"), "Collection ID changed");
                if (path.equals("/artist/songs")) require(parameters.get("order").equals("hot"), "Artist tracks lost their sort order");
                int end = Math.min(offset + limit, 3);
                response = "{\"code\":200,\"songs\":[" + String.join(",", java.util.stream.IntStream.range(Math.min(offset, 3), end)
                    .mapToObj(i -> neteaseSong(101 + i)).toList()) + "],\"privileges\":[{\"id\":101,\"fee\":1}]"
                    + (path.equals("/artist/songs") ? ",\"more\":" + (end < 3) : "") + "}";
            } else {
                require(exchange.getRequestHeaders().getFirst("Cookie") == null && !parameters.containsKey("cookie"), "NetEase credentials reached Audius");
                require(parameters.get("app_name").equals("Pupper Client"), "Audius app identity missing");
                if (path.endsWith("/search")) {
                    require(parameters.get("query").equals("Night 中文"), "Audius typed query lost Unicode");
                    response = "{\"data\":[" + (path.equals("/v1/users/search")
                        ? "{\"id\":\"AbC\",\"name\":\"Artist 中文\",\"handle\":\"artist\",\"track_count\":3,\"profile_picture\":{\"150x150\":\"" + origin + "/cover\"}}"
                        : "{\"id\":\"AbC\",\"playlist_name\":\"Night 中文\",\"track_count\":3,\"user\":{\"name\":\"Curator\"},\"artwork\":{\"480x480\":\"" + origin + "/cover\"}}") + ",{\"id\":\"../bad\",\"name\":\"Invalid\"}]}";
                } else if (path.equals("/v1/playlists/AbC/tracks")) {
                    require(!parameters.containsKey("offset") && !parameters.containsKey("limit"), "Invented server pagination for the Audius playlist-tracks route");
                    playlistCalls.incrementAndGet(); response = "{\"data\":[" + audiusSong(1, false) + "," + audiusSong(1, false) + "," + audiusSong(3, false) + "]}";
                } else if (path.equals("/v1/users/AbC/tracks")) {
                    response = "{\"data\":[" + String.join(",", java.util.stream.IntStream.range(Math.min(offset, 3), Math.min(offset + limit, 3))
                        .mapToObj(i -> audiusSong(i + 1, i == 1)).toList()) + "]}";
                } else throw new AssertionError("Unexpected catalogue route: " + path);
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start(); Language previous = I18n.getCurrentLanguage();
        try {
            MusicProvider netease = new NeteaseMusicProvider(new NeteaseMusicApi(URI.create(origin)));
            var artists = netease.search("Night 中文", MusicSearchType.ARTISTS, 2, 6);
            var playlists = netease.search("Night 中文", MusicSearchType.PLAYLISTS, 2, 6);
            MusicCollection artist = artists.collections().getFirst(), playlist = playlists.collections().getFirst();
            require(artists.tracks().isEmpty() && artists.collections().size() == 1 && artists.total() == 51 && artists.nextOffset() == 8,
                "Artist search became songs or paged by accepted rather than raw records");
            require(artist.name().equals("Artist 中文") && artist.trackCount() == 3 && artist.coverUrl().endsWith("/cover"), "Artist metadata lost");
            require(playlist.owner().equals("Curator") && playlist.trackCount() == 3 && playlist.type() == MusicSearchType.PLAYLISTS, "Playlist was parsed as an artist");
            require(!artist.key().equals(playlist.key()) && !artist.coverFilename().equals(playlist.coverFilename()), "Artist/playlist namespaces collide");
            require(netease.search("Night 中文", 2, 6).tracks().getFirst().title().equals("Song 101"), "Existing command song search changed type");
            fallback.set(true);
            require(netease.search("Night 中文", MusicSearchType.ARTISTS, 2, 6).collections().equals(artists.collections()), "Artist search lost cloudsearch fallback");
            require(netease.search("Night 中文", MusicSearchType.PLAYLISTS, 2, 6).collections().equals(playlists.collections()), "Playlist search fallback changed its type");
            fallback.set(false);
            MusicProvider.SearchResult first = netease.collectionTracks(artist, 2, 0, "fixture-cookie"), last = netease.collectionTracks(artist, 2, 2, "fixture-cookie");
            require(first.total() == -1 && first.nextOffset() == 2 && last.total() == 3 && last.tracks().getFirst().id() == 103,
                "Artist more flag or page order lost");
            require(first.tracks().getFirst().fee() == 1, "Browsing discarded VIP detail privileges");
            require(netease.collectionTracks(playlist, 2, 2, "fixture-cookie").total() == 3 && authenticated.get() == 3,
                "Playlist end or authenticated routing lost");
            MusicProvider audius = new AudiusMusicProvider(URI.create(origin + "/v1/"));
            var foreignArtists = audius.search("Night 中文", MusicSearchType.ARTISTS, 2, 6);
            var foreignPlaylists = audius.search("Night 中文", MusicSearchType.PLAYLISTS, 2, 6);
            MusicCollection foreignArtist = foreignArtists.collections().getFirst(), foreignPlaylist = foreignPlaylists.collections().getFirst();
            require(foreignArtists.total() == -1 && foreignArtists.nextOffset() == 8 && foreignArtist.owner().equals("@artist"), "Audius user search schema/pagination lost");
            require(foreignPlaylists.collections().size() == 1 && foreignPlaylist.owner().equals("Curator") && !foreignArtist.key().equals(foreignPlaylist.key()),
                "Audius playlist metadata or entity identity lost");
            var publicSongs = audius.collectionTracks(foreignArtist, 2, 0, "fixture-cookie");
            require(publicSongs.tracks().size() == 2 && publicSongs.tracks().getFirst().playable() && !publicSongs.tracks().get(1).playable(), "Artist tracks discarded gated access flags");
            require(audius.collectionTracks(foreignArtist, 2, 2, null).total() == 3, "Audius artist final page not detected");
            var listFirst = audius.collectionTracks(foreignPlaylist, 2, 0, "fixture-cookie");
            var listLast = audius.collectionTracks(foreignPlaylist, 2, 2, null);
            require(listFirst.total() == 3 && listLast.total() == 3 && listLast.nextOffset() == 3 && listLast.tracks().getFirst().providerId().equals("T3"),
                "Full Audius playlist response was repeated instead of sliced into pages");
            require(playlistCalls.get() == 1 && listFirst.tracks().getFirst().sameSong(listFirst.tracks().get(1)), "Playlist snapshot was refetched or intentional duplicates lost");
            audius.collectionTracks(foreignPlaylist, 2, 0, null); require(playlistCalls.get() == 2, "Reopening a playlist failed to refresh its snapshot");
            require(audius.collectionTracks(foreignPlaylist, 2, 99, null).tracks().isEmpty(), "Out-of-range playlist page wrapped to its beginning");
            var store = new MusicLibraryStore(root.resolve("music"));
            var covers = new MusicDownload(audius, store, root.resolve("music"), root.resolve("cache"));
            covers.fetchCover(foreignArtist); covers.fetchCover(foreignPlaylist);
            require(Files.isRegularFile(covers.cover(foreignArtist)) && Files.isRegularFile(covers.cover(foreignPlaylist)), "Collection artwork was not cached independently");
            try { new MusicCollection("audius", "../bad", MusicSearchType.ARTISTS, "Bad", "", "", -1); throw new AssertionError("Unsafe entity ID accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
            var lower = new MusicCollection("audius", "abc", MusicSearchType.ARTISTS, "Artist", "", "", -1);
            require(!lower.coverFilename().equalsIgnoreCase(foreignArtist.coverFilename()), "Case-sensitive entity IDs collided on Windows");
            stateChecks(artist, artists, first, foreignArtist);
            var entries = java.util.stream.Stream.concat(listFirst.tracks().stream(), listLast.tracks().stream())
                .map(track -> new MusicQueue.Entry(track, "")).toList();
            var saved = store.createPlaylist("Remote copy", entries); var queue = new MusicQueue(); queue.start(entries, 0);
            require(queue.snapshot().upcoming().size() == 2, "Queue copy discarded intentional duplicate occurrences");
            require(new MusicLibraryStore(root.resolve("music")).playlist(saved.id()).entries().stream().map(entry -> entry.track().providerId()).toList().equals(List.of("T1", "T3")),
                "Remote copy failed to preserve the local playlist's unique-song storage contract");
            for (Language language : Language.values()) {
                if (language != Language.ENGLISH && language != Language.CHINESE) continue;
                I18n.setLanguage(language);
                for (MusicSearchType type : MusicSearchType.values()) require(!MusicText.get(type.nameKey()).contains("music."), "Missing search type translation");
                require(!MusicText.get("music.search.results.for", "Night", MusicText.get(MusicSearchType.ARTISTS.nameKey()), 51).contains("%"), "Typed result heading lost argument order");
                require(MusicText.collection(playlist).contains("Curator") && MusicText.collection(playlist).contains("3"), "Collection details omitted creator or song count");
                require(!MusicText.get("music.browse.saveloaded").contains("music.") && !MusicText.get("music.browse.save").contains("music."), "Remote save action untranslated");
            }
            var compact = MusicPlayerLayout.content(MusicPlayerLayout.Panel.QUEUE);
            for (MusicSearchType type : MusicSearchType.values()) {
                var box = MusicPlayerLayout.searchType(compact.x(), compact.y(), type.ordinal());
                require(box.height() >= 48 && box.x() + box.width() <= compact.x() + compact.width() && box.contains(box.x() + 1, box.y() + 1), "Compact filter touch targets overlap the side panel");
            }
            require(MusicPlayerLayout.listOffset(true, false) >= 96 && MusicPlayerLayout.listOffset(false, true) > 140, "Result rows overlap filters or collection header");
            System.out.println("Music catalogue checks passed: " + checks + " assertions; typed provider search, artist/playlist paging, access, artwork, queue copies and stale replies.");
        } finally {
            server.stop(0); if (previous != null) I18n.setLanguage(previous);
            try (var files = Files.walk(root)) { for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
    }
    private static void stateChecks(MusicCollection artist, MusicProvider.CatalogResult artists, MusicProvider.SearchResult songs, MusicCollection foreignArtist) {
        var state = new MusicSearchState("netease"); state.reset("netease", MusicSearchType.SONGS, "Night 中文"); var old = state.begin(false);
        state.reset("netease", MusicSearchType.ARTISTS, "Night 中文"); var current = state.begin(false);
        require(!state.accept(old, new MusicProvider.CatalogResult(MusicSearchType.SONGS, songs.tracks(), List.of(), 3, 0, 2)), "Old song reply overwrote artist search");
        require(state.accept(current, new MusicProvider.CatalogResult(MusicSearchType.ARTISTS, List.of(), artists.collections(), 51, 0, 2)), "Current artist results rejected");
        state.open(artist); var detail = state.begin(false); state.back();
        require(!state.accept(detail, songs) && state.query().equals("Night 中文") && state.type() == MusicSearchType.ARTISTS && state.collections().size() == 1 && state.nextOffset() == 2,
            "Back lost root results/query/page or accepted a late detail reply");
        var more = state.begin(true); require(more.offset() == 2 && state.begin(true) == null, "Pagination restarted at zero or allowed duplicate loads");
        require(state.accept(more, new MusicProvider.CatalogResult(MusicSearchType.ARTISTS, List.of(), List.of(), -1, 2, 2)) && !state.hasMore(), "Empty unknown-total page left endless Load More");
        state.open(artist); var pending = state.begin(false); state.reset("audius", MusicSearchType.ARTISTS, "Night 中文");
        require(!state.accept(pending, songs) && !state.fail(pending), "A stale source reply/error changed the new source state");
        state.open(foreignArtist); pending = state.begin(false);
        require(state.accept(pending, songs) && state.tracks().size() == 2 && state.hasMore(), "Current detail tracks or pagination lost");
        var last = state.begin(true); state.back(); require(!state.accept(last, songs) && !state.loading(), "Late detail page appeared after returning to search");
        state.reset("audius", MusicSearchType.PLAYLISTS, "Other"); var cancelled = state.begin(false); state.cancel();
        require(!state.searched() && !state.fail(cancelled) && state.begin(false) != null, "Navigation cancelled search permanently or let a stale error through");
    }
    private static String neteaseSong(int id) { return "{\"id\":" + id + ",\"name\":\"Song " + id + "\",\"dt\":120000,\"ar\":[{\"name\":\"Artist\"}]}"; }
    private static String audiusSong(int id, boolean gated) { return "{\"id\":\"T" + id + "\",\"title\":\"Song " + id + "\",\"is_streamable\":true,\"is_stream_gated\":" + gated + ",\"user\":{\"name\":\"Artist\"}}"; }
    private static Map<String, String> params(String text) {
        var result = new java.util.HashMap<String, String>();
        if (text != null && !text.isBlank()) for (String part : text.split("&")) {
            var values = part.split("=", 2); result.put(URLDecoder.decode(values[0], StandardCharsets.UTF_8), values.length < 2 ? "" : URLDecoder.decode(values[1], StandardCharsets.UTF_8));
        }
        return result;
    }
    private static boolean queryContains(String query, String value) { return query != null && query.contains(value); }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
