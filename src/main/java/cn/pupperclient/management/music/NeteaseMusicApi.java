package cn.pupperclient.management.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One API origin for search, metadata, audio URLs and account favorites. */
public final class NeteaseMusicApi {
    public record SearchResult(List<MusicTrack> tracks, int total, int offset) { }
    public record AudioSource(URI uri, String extension, int fee, long previewMillis) { }
    public record Lyrics(String original, String translated) { }
    private final URI base;

    public NeteaseMusicApi(URI base) {
        this.base = URI.create(base.toString().replaceAll("/+$", "") + "/");
    }

    public SearchResult search(String keyword, int limit, int offset) throws MusicError {
        Map<String, String> params = Map.of("keywords", keyword, "type", "1", "limit", String.valueOf(limit),
            "offset", String.valueOf(offset));
        JsonObject response;
        try {
            response = request("cloudsearch", params, null);
        } catch (MusicError unavailable) {
            response = request("search", params, null);
        }
        JsonObject result = object(response, "result");
        List<MusicTrack> tracks = parseTracks(array(result, "songs"));
        return new SearchResult(tracks, (int) number(result, "songCount", offset + tracks.size()), offset);
    }

    public List<MusicTrack> details(List<Long> ids) throws MusicError {
        List<MusicTrack> tracks = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += 100) {
            String joined = String.join(",", ids.subList(start, Math.min(start + 100, ids.size()))
                .stream().map(String::valueOf).toList());
            JsonObject response = request("song/detail", Map.of("ids", joined), null);
            tracks.addAll(parseTracks(array(response, "songs"), array(response, "privileges")));
        }
        return List.copyOf(tracks);
    }

    public AudioSource audio(long id, String quality, String cookie) throws MusicError {
        JsonArray data = array(request("song/url/v1", Map.of("id", String.valueOf(id), "level", quality), cookie), "data");
        if (data.isEmpty() || !data.get(0).isJsonObject()) throw new MusicError("music.error.unavailable");
        JsonObject song = data.get(0).getAsJsonObject();
        String url = string(song, "url");
        if (url.isBlank()) throw new MusicError("music.error.unavailable");
        // The decoder supports MP3 and FLAC. Never give AAC/M4A bytes an .mp3 extension.
        String type = string(song, "type").toLowerCase(java.util.Locale.ROOT);
        if (!type.equals("mp3") && !type.equals("flac")) throw new MusicError("music.error.format");
        try {
            return new AudioSource(URI.create(url), "." + type, (int) number(song, "fee", -1), previewMillis(song));
        } catch (IllegalArgumentException invalid) {
            throw new MusicError("music.error.unavailable");
        }
    }

    public List<Long> likes(String userId, String cookie) throws MusicError {
        if (cookie == null || cookie.isBlank() || userId == null) throw new MusicError("music.error.login");
        return array(request("likelist", Map.of("uid", userId), cookie), "ids")
            .asList().stream().map(JsonElement::getAsLong).toList();
    }

    public Lyrics lyrics(long id) throws MusicError {
        JsonObject response = request("lyric", Map.of("id", String.valueOf(id)), null);
        return new Lyrics(string(object(response, "lrc"), "lyric"), string(object(response, "tlyric"), "lyric"));
    }

    public void like(long id, boolean liked, String cookie) throws MusicError {
        if (cookie == null || cookie.isBlank()) throw new MusicError("music.error.login");
        request("like", Map.of("id", String.valueOf(id), "like", String.valueOf(liked),
            "timestamp", String.valueOf(System.currentTimeMillis())), cookie);
    }

    private JsonObject request(String path, Map<String, String> parameters, String cookie) throws MusicError {
        HttpURLConnection connection = null;
        try {
            Map<String, String> params = new LinkedHashMap<>(parameters);
            boolean authenticated = cookie != null && !cookie.isBlank();
            if (authenticated) params.put("cookie", cookie);
            String body = form(params);
            URI endpoint = base.resolve(path + (authenticated ? "" : "?" + body));
            connection = open(endpoint);
            if (authenticated) {
                // Keep the account cookie in the request body, out of URL/access logs.
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                try (OutputStream stream = connection.getOutputStream()) {
                    stream.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            if (status == 401 || status == 403) throw new MusicError("music.error.login");
            if (status != 200) throw new MusicError("music.error.network");
            try (InputStream stream = connection.getInputStream()) {
                JsonObject response = JsonParser.parseString(new String(stream.readNBytes(8 * 1024 * 1024),
                    StandardCharsets.UTF_8)).getAsJsonObject();
                long code = number(response, "code", 200);
                if (code == 301 || code == 401 || code == 403) throw new MusicError("music.error.login");
                if (code != 200) throw new MusicError("music.error.network");
                return response;
            }
        } catch (IOException | RuntimeException failure) {
            throw new MusicError("music.error.network");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static HttpURLConnection open(URI uri) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
            throw new IOException("Unsupported music URL scheme");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("User-Agent", "Pupper Client");
        return connection;
    }

    public static List<MusicTrack> parseTracks(JsonArray songs) {
        return parseTracks(songs, new JsonArray());
    }
    private static List<MusicTrack> parseTracks(JsonArray songs, JsonArray privileges) {
        Map<Long, JsonObject> access = new LinkedHashMap<>();
        for (JsonElement element : privileges) if (element.isJsonObject()) {
            JsonObject privilege = element.getAsJsonObject(); access.put(number(privilege, "id", 0), privilege);
        }
        List<MusicTrack> tracks = new ArrayList<>();
        for (JsonElement element : songs) {
            if (!element.isJsonObject()) continue;
            JsonObject song = element.getAsJsonObject();
            long id = number(song, "id", 0);
            String title = string(song, "name");
            if (id <= 0 || title.isBlank()) continue;
            JsonArray artists = song.has("ar") ? array(song, "ar") : array(song, "artists");
            String artist = String.join(", ", artists.asList().stream().filter(JsonElement::isJsonObject)
                .map(a -> string(a.getAsJsonObject(), "name")).filter(s -> !s.isBlank()).toList());
            JsonObject album = object(song, song.has("al") ? "al" : "album");
            JsonObject privilege = access.getOrDefault(id, object(song, "privilege"));
            tracks.add(new MusicTrack(id, title, artist, string(album, "name"), string(album, "picUrl"),
                number(song, "dt", number(song, "duration", 0)), "netease", Long.toString(id), true, true,
                (int) number(privilege, "fee", number(song, "fee", 0)), previewMillis(song)));
        }
        return List.copyOf(tracks);
    }

    private static long previewMillis(JsonObject song) {
        JsonElement trial = song.get("freeTrialInfo");
        if (trial == null || !trial.isJsonObject()) trial = song.get("trialInfo");
        if (trial == null || !trial.isJsonObject()) return 0;
        JsonObject info = trial.getAsJsonObject();
        long start = number(info, "start", 0), end = number(info, "end", 0);
        if (start >= 0 && end > start && end - start <= Long.MAX_VALUE / 1000) return (end - start) * 1000;
        long time = number(song, "time", 0);
        return time > 0 ? time : -1;
    }

    private static String form(Map<String, String> params) {
        return String.join("&", params.entrySet().stream().map(e ->
            URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" +
            URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).toList());
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static long number(JsonObject object, String key, long fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsLong() : fallback;
        } catch (RuntimeException invalid) { return fallback; }
    }
}
