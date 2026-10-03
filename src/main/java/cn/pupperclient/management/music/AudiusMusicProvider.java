package cn.pupperclient.management.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Public Audius Discovery API. Restricted tracks remain visible, with unavailable actions disabled. */
public final class AudiusMusicProvider implements MusicProvider {
    private final URI base;
    private record PlaylistTracks(String id, JsonArray songs) { }
    private volatile PlaylistTracks playlistTracks;
    public AudiusMusicProvider() { this(URI.create("https://api.audius.co/v1/")); }
    public AudiusMusicProvider(URI base) { this.base = URI.create(base.toString().replaceAll("/+$", "") + "/"); }
    @Override public String id() { return "audius"; }
    @Override public List<String> qualities() { return List.of("standard"); }

    @Override public CatalogResult search(String keyword, MusicSearchType type, int limit, int offset) throws MusicError {
        if (type == MusicSearchType.SONGS) return MusicProvider.super.search(keyword, type, limit, offset);
        limit = Math.clamp(limit, 1, 50); offset = Math.max(0, offset);
        JsonArray data = array(request((type == MusicSearchType.ARTISTS ? "users" : "playlists")
            + "/search?query=" + encode(keyword) + "&limit=" + limit + "&offset=" + offset), "data");
        var collections = new ArrayList<MusicCollection>();
        for (JsonElement element : data) if (element.isJsonObject()) {
            JsonObject item = element.getAsJsonObject();
            String id = string(item, "id"), name = string(item, type == MusicSearchType.ARTISTS ? "name" : "playlist_name");
            if (!id.matches("[a-zA-Z0-9_-]{1,80}") || name.isBlank()) continue;
            String owner = type == MusicSearchType.ARTISTS ? string(item, "handle") : string(object(item, "user"), "name");
            if (type == MusicSearchType.ARTISTS && !owner.isBlank()) owner = "@" + owner;
            collections.add(new MusicCollection(id(), id, type, name, owner,
                artwork(object(item, type == MusicSearchType.ARTISTS ? "profile_picture" : "artwork")), integer(item, "track_count", -1)));
        }
        return new CatalogResult(type, List.of(), collections, data.size() == limit ? -1 : offset + data.size(), offset, offset + data.size());
    }

    @Override public SearchResult collectionTracks(MusicCollection collection, int limit, int offset, String cookie) throws MusicError {
        if (!collection.provider().equals(id())) throw new MusicError("music.error.metadata");
        validId(collection.id()); limit = Math.clamp(limit, 1, 50); offset = Math.max(0, offset);
        JsonArray data; int start = 0, end, total;
        if (collection.type() == MusicSearchType.PLAYLISTS) {
            // The official playlist-tracks route has no limit/offset parameters. Keep one snapshot for its subsequent pages.
            PlaylistTracks cached = playlistTracks;
            if (offset == 0 || cached == null || !cached.id().equals(collection.id())) {
                cached = new PlaylistTracks(collection.id(), array(request("playlists/" + collection.id() + "/tracks"), "data"));
                playlistTracks = cached;
            }
            data = cached.songs(); start = Math.min(offset, data.size()); end = Math.min(start + limit, data.size()); total = data.size();
        } else {
            data = array(request("users/" + collection.id() + "/tracks?limit=" + limit + "&offset=" + offset), "data");
            end = data.size(); total = data.size() == limit ? -1 : offset + data.size();
        }
        var tracks = new ArrayList<MusicTrack>();
        for (int i = start; i < end; i++) {
            JsonElement element = data.get(i); if (!element.isJsonObject()) continue;
            MusicTrack track = parse(element.getAsJsonObject()); if (track != null) tracks.add(track);
        }
        return new SearchResult(List.copyOf(tracks), total, offset, offset + end - start);
    }

    @Override public SearchResult search(String keyword, int limit, int offset) throws MusicError {
        limit = Math.clamp(limit, 1, 50);
        offset = Math.max(0, offset);
        JsonObject response = request("tracks/search?query=" + encode(keyword) + "&limit=" + limit + "&offset=" + offset);
        List<MusicTrack> tracks = new ArrayList<>();
        JsonArray data = response.has("data") && response.get("data").isJsonArray()
            ? response.getAsJsonArray("data") : new JsonArray();
        for (JsonElement item : data) if (item.isJsonObject()) {
            MusicTrack track = parse(item.getAsJsonObject());
            if (track != null) tracks.add(track);
        }
        // Audius does not return a total. A full page advertises one more page, without inventing a song count.
        return new SearchResult(List.copyOf(tracks), data.size() == limit ? -1 : offset + tracks.size(), offset, offset + data.size());
    }

    @Override public MusicTrack track(String id) throws MusicError {
        validId(id);
        MusicTrack track = parse(object(request("tracks/" + id), "data"));
        if (track == null) throw new MusicError("music.error.metadata");
        return track;
    }

    @Override public AudioSource audio(MusicTrack searched, String quality, String cookie, boolean download) throws MusicError {
        // Refresh access at the time of use: artist permissions may have changed since the search or favorite.
        MusicTrack current = track(searched.providerId());
        if (download ? !current.downloadable() : !current.playable())
            throw new MusicError(download ? "music.error.downloadrestricted" : "music.error.playrestricted");
        return new AudioSource(endpoint("tracks/" + current.providerId() + (download ? "/download" : "/stream")), ".mp3");
    }

    private JsonObject request(String path) throws MusicError {
        HttpURLConnection connection = null;
        try {
            connection = NeteaseMusicApi.open(endpoint(path));
            int status = connection.getResponseCode();
            if (status == 429) throw new MusicError("music.error.ratelimit");
            if (status == 401 || status == 403) throw new MusicError("music.error.playrestricted");
            if (status == 404) throw new MusicError("music.error.unavailable");
            if (status != 200) throw new MusicError("music.error.network");
            try (var input = connection.getInputStream()) {
                return JsonParser.parseString(new String(input.readNBytes(8 * 1024 * 1024), StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (IOException | RuntimeException invalid) { throw new MusicError("music.error.network"); }
        finally { if (connection != null) connection.disconnect(); }
    }

    private URI endpoint(String path) {
        return base.resolve(path + (path.contains("?") ? "&" : "?") + "app_name=Pupper%20Client");
    }
    private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    private static void validId(String id) throws MusicError {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,80}")) throw new MusicError("music.error.metadata");
    }
    public static MusicTrack parse(JsonObject data) {
        String id = string(data, "id"), title = string(data, "title");
        if (!id.matches("[a-zA-Z0-9_-]{1,80}") || title.isBlank()) return null;
        JsonObject artwork = object(data, "artwork"), access = object(data, "access");
        String cover = string(artwork, "480x480");
        if (cover.isBlank()) cover = string(artwork, "150x150");
        boolean playable = bool(data, "is_streamable", false) && !bool(data, "is_stream_gated", false)
            && bool(access, "stream", true);
        boolean downloadable = bool(data, "is_downloadable", false) && !bool(data, "is_download_gated", false)
            && bool(access, "download", true);
        long duration = 0;
        try { duration = Math.max(0, data.get("duration").getAsLong()) * 1000; } catch (RuntimeException ignored) { }
        return new MusicTrack(0, title, string(object(data, "user"), "name"), "", cover, duration,
            "audius", id, playable, downloadable);
    }
    private static String string(JsonObject data, String key) {
        JsonElement value = data.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
    private static JsonArray array(JsonObject data, String key) {
        return data.has(key) && data.get(key).isJsonArray() ? data.getAsJsonArray(key) : new JsonArray();
    }
    private static int integer(JsonObject data, String key, int fallback) {
        try { return data.get(key).getAsInt(); } catch (RuntimeException invalid) { return fallback; }
    }
    private static String artwork(JsonObject data) {
        String cover = string(data, "480x480");
        return cover.isBlank() ? string(data, "150x150") : cover;
    }
    private static JsonObject object(JsonObject data, String key) {
        return data.has(key) && data.get(key).isJsonObject() ? data.getAsJsonObject(key) : new JsonObject();
    }
    private static boolean bool(JsonObject data, String key, boolean fallback) {
        return data.has(key) && data.get(key).isJsonPrimitive() ? data.get(key).getAsBoolean() : fallback;
    }
}
