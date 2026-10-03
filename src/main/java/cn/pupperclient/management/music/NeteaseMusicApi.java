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
    public static final URI DEFAULT_ORIGIN = URI.create("https://zm.wwoyun.cn/");
    public record SearchResult(List<MusicTrack> tracks, int total, int offset) { }
    public record AudioSource(URI uri, String extension, int fee, long previewMillis) { }
    public record Lyrics(String original, String translated) { }
    public record QrCode(String key, String url, String image) {
        @Override public String toString() { return "QrCode[generated]"; }
    }
    public record QrCheck(int code, String cookie) {
        @Override public String toString() { return "QrCheck[code=" + code + "]"; }
    }
    private final URI base;

    public NeteaseMusicApi(URI base) {
        this.base = URI.create(base.toString().replaceAll("/+$", "") + "/");
    }

    public SearchResult search(String keyword, int limit, int offset) throws MusicError {
        var result = search(keyword, MusicSearchType.SONGS, limit, offset);
        return new SearchResult(result.tracks(), result.total(), result.offset());
    }

    public MusicProvider.CatalogResult search(String keyword, MusicSearchType type, int limit, int offset) throws MusicError {
        limit = Math.clamp(limit, 1, 50); offset = Math.max(0, offset);
        String apiType = switch (type) { case SONGS -> "1"; case ARTISTS -> "100"; case PLAYLISTS -> "1000"; };
        Map<String, String> params = Map.of("keywords", keyword, "type", apiType, "limit", String.valueOf(limit),
            "offset", String.valueOf(offset));
        JsonObject response;
        try {
            response = request("cloudsearch", params, null);
        } catch (MusicError unavailable) {
            response = request("search", params, null);
        }
        JsonObject result = object(response, "result");
        String field = switch (type) { case SONGS -> "songs"; case ARTISTS -> "artists"; case PLAYLISTS -> "playlists"; };
        JsonArray data = array(result, field);
        int next = offset + data.size();
        int total = (int) number(result, switch (type) { case SONGS -> "songCount"; case ARTISTS -> "artistCount"; case PLAYLISTS -> "playlistCount"; },
            pageTotal(result, limit, offset, data.size()));
        return new MusicProvider.CatalogResult(type, type == MusicSearchType.SONGS ? parseTracks(data) : List.of(),
            type == MusicSearchType.SONGS ? List.of() : parseCollections(data, type), total, offset, next);
    }

    public MusicProvider.SearchResult collectionTracks(MusicCollection collection, int limit, int offset, String cookie) throws MusicError {
        if (!collection.provider().equals("netease") || !collection.id().matches("[1-9][0-9]{0,18}")) throw new MusicError("music.error.metadata");
        limit = Math.clamp(limit, 1, 50); offset = Math.max(0, offset);
        Map<String, String> parameters = new LinkedHashMap<>(Map.of("id", collection.id(), "limit", String.valueOf(limit), "offset", String.valueOf(offset)));
        if (collection.type() == MusicSearchType.ARTISTS) parameters.put("order", "hot");
        JsonObject response = request(collection.type() == MusicSearchType.ARTISTS ? "artist/songs" : "playlist/track/all", parameters, cookie);
        JsonArray data = array(response, "songs");
        return new MusicProvider.SearchResult(parseTracks(data, array(response, "privileges")),
            (int) number(response, "total", pageTotal(response, limit, offset, data.size())), offset, offset + data.size());
    }

    private static int pageTotal(JsonObject response, int limit, int offset, int count) {
        if (response.has("more") && response.get("more").isJsonPrimitive()) try {
            return response.get("more").getAsBoolean() ? -1 : offset + count;
        } catch (RuntimeException invalid) { /* Fall back to the received page length. */ }
        return count == limit ? -1 : offset + count;
    }

    private static List<MusicCollection> parseCollections(JsonArray data, MusicSearchType type) {
        var collections = new ArrayList<MusicCollection>();
        for (JsonElement element : data) if (element.isJsonObject()) {
            JsonObject item = element.getAsJsonObject(); long id = number(item, "id", 0);
            String name = string(item, "name"); if (id <= 0 || name.isBlank()) continue;
            String cover = string(item, type == MusicSearchType.ARTISTS ? "img1v1Url" : "coverImgUrl");
            if (cover.isBlank()) cover = string(item, "picUrl");
            collections.add(new MusicCollection("netease", Long.toString(id), type, name,
                type == MusicSearchType.ARTISTS ? "" : string(object(item, "creator"), "nickname"), cover,
                (int) number(item, type == MusicSearchType.ARTISTS ? "musicSize" : "trackCount", -1)));
        }
        return List.copyOf(collections);
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

    public void sendCaptcha(String phone) throws MusicError {
        validatePhone(phone);
        authRequest("captcha/sent", Map.of("phone", phone), null, false);
    }
    public MusicAccount phoneLogin(String phone, String captcha) throws MusicError {
        validatePhone(phone);
        if (captcha == null || !captcha.matches("[0-9]{4,8}")) throw new MusicError("music.login.error.input");
        JsonObject response = authRequest("login/cellphone", Map.of("phone", phone, "captcha", captcha), null, false);
        String cookie = string(response, "cookie");
        if (cookie.isBlank()) throw new MusicError("music.login.error.response");
        MusicAccount account = parseAccount(response, cookie, phone);
        return account.authenticated() ? account : loginAccount(cookie, phone);
    }
    public MusicAccount loginAccount(String cookie, String phone) throws MusicError {
        MusicAccount account = parseAccount(authRequest("user/account", Map.of(), cookie, false), cookie, phone);
        if (!account.authenticated()) throw new MusicError("music.login.error.response");
        return account;
    }
    public MusicAccount loginStatus(String cookie, String phone) throws MusicError {
        try {
            JsonObject data = object(authRequest("login/status", Map.of(), cookie, false), "data");
            long code = number(data, "code", -1);
            if (code == 301 || code == 401 || code == 403) return MusicAccount.GUEST;
            if (code != 200) throw new MusicError("music.login.error.response");
            // The API also returns data.code=200 for anonymous visitors, with null account/profile.
            MusicAccount account = parseAccount(data, cookie, phone);
            return account.authenticated() ? account : MusicAccount.GUEST;
        } catch (MusicError error) {
            if (error.key().equals("music.error.login")) return MusicAccount.GUEST;
            throw error;
        }
    }
    public String refreshLogin(String cookie) throws MusicError {
        String refreshed = string(authRequest("login/refresh", Map.of(), cookie, false), "cookie");
        return refreshed.isBlank() ? cookie : refreshed;
    }
    public void logout(String cookie) throws MusicError { authRequest("logout", Map.of(), cookie, false); }
    public QrCode createLoginQr() throws MusicError {
        JsonObject keyData = object(authRequest("login/qr/key", Map.of(), null, false), "data");
        if (number(keyData, "code", -1) != 200) throw new MusicError("music.login.error.response");
        String key = string(keyData, "unikey");
        if (key.isBlank()) throw new MusicError("music.login.error.response");
        JsonObject data = object(authRequest("login/qr/create", Map.of("key", key, "qrimg", "1"), null, false), "data");
        String url = string(data, "qrurl"), image = string(data, "qrimg");
        if (url.isBlank() || image.isBlank()) throw new MusicError("music.login.error.response");
        return new QrCode(key, url, image);
    }
    public QrCheck checkLoginQr(String key) throws MusicError {
        JsonObject response = authRequest("login/qr/check", Map.of("key", key), null, true);
        int code = (int) number(response, "code", -1);
        String cookie = string(response, "cookie");
        if (code < 800 || code > 803 || (code == 803 && cookie.isBlank())) throw new MusicError("music.login.error.response");
        return new QrCheck(code, cookie);
    }
    private JsonObject authRequest(String path, Map<String, String> parameters, String cookie, boolean qr) throws MusicError {
        Map<String, String> params = new LinkedHashMap<>(parameters);
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return request(path, params, cookie, true, qr);
    }
    private static void validatePhone(String phone) throws MusicError {
        if (phone == null || !phone.matches("[0-9]{5,20}")) throw new MusicError("music.login.error.input");
    }
    private static MusicAccount parseAccount(JsonObject response, String cookie, String phone) {
        JsonObject account = object(response, "account"), profile = object(response, "profile");
        long id = number(account, "id", number(profile, "userId", 0));
        return new MusicAccount(cookie, id > 0 ? Long.toString(id) : "", string(profile, "nickname"), phone);
    }

    private JsonObject request(String path, Map<String, String> parameters, String cookie) throws MusicError {
        return request(path, parameters, cookie, cookie != null && !cookie.isBlank(), false);
    }
    private JsonObject request(String path, Map<String, String> parameters, String cookie, boolean post, boolean qr) throws MusicError {
        HttpURLConnection connection = null;
        try {
            Map<String, String> params = new LinkedHashMap<>(parameters);
            boolean authenticated = cookie != null && !cookie.isBlank();
            if (authenticated) params.put("cookie", cookie);
            String body = form(params);
            URI endpoint = base.resolve(path + (post ? "" : "?" + body));
            connection = open(endpoint);
            if (post) {
                // Keep cookies, phone numbers, captcha and QR keys out of request URLs/access logs.
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
                if (code != 200 && !(qr && code >= 800 && code <= 803))
                    throw new MusicError(post && path.startsWith("login/") ? "music.login.error.response" : "music.error.network");
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
