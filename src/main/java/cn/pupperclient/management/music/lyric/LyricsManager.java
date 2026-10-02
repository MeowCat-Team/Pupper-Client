package cn.pupperclient.management.music.lyric;

import cn.pupperclient.management.music.Music;
import cn.pupperclient.management.music.NeteaseMusicApi;
import cn.pupperclient.libraries.flac.FLACDecoder;
import cn.pupperclient.libraries.flac.metadata.VorbisComment;
import com.mpatric.mp3agic.Mp3File;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/** Network/tag reads run outside rendering and ticks. One cache serves the player and HUD. */
public final class LyricsManager {
    public enum State { LOADING, READY, EMPTY, ERROR }
    public record Result(State state, SongLyrics lyrics) { }
    @FunctionalInterface public interface Source { NeteaseMusicApi.Lyrics load(long id) throws Exception; }
    private static final class Entry { volatile Result result = new Result(State.LOADING, SongLyrics.EMPTY); }
    private static final Gson GSON = new Gson();
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Source source;
    private final Path cache;
    private final Executor executor;

    public LyricsManager(Source source, Path cache, Executor executor) {
        this.source = source; this.cache = cache; this.executor = executor;
    }

    public Result get(Music music) {
        if (music == null) return new Result(State.EMPTY, SongLyrics.EMPTY);
        String key = key(music);
        Entry pending = new Entry();
        Entry entry = entries.putIfAbsent(key, pending);
        if (entry == null) {
            executor.execute(() -> pending.result = load(music));
            entry = pending;
        }
        return entry.result;
    }

    public List<LyricLine> getLyrics(Music music) { return get(music).lyrics().lines(); }
    public String getCurrentLyric(Music music, float currentTime) {
        SongLyrics lyrics = get(music).lyrics();
        int index = lyrics.currentIndex(currentTime);
        return index < 0 ? "" : lyrics.lines().get(index).getText();
    }
    public void retry(Music music) { if (music != null) entries.remove(key(music)); }
    public void clearCache() { entries.clear(); }

    private String key(Music music) {
        return music.getTrack().id() > 0 ? "netease:" + music.getTrack().id() : music.getAudio().getAbsolutePath();
    }

    private Result load(Music music) {
        long id = music.getTrack().id();
        SongLyrics local = local(music.getAudio().toPath());
        if (!local.isEmpty()) return new Result(State.READY, local);
        if (id <= 0) return new Result(State.EMPTY, SongLyrics.EMPTY);
        Path file = cache.resolve("ncm-lyrics-" + id + ".json");
        try {
            if (Files.isRegularFile(file)) {
                var cached = GSON.fromJson(Files.readString(file), NeteaseMusicApi.Lyrics.class);
                if (cached != null) {
                    SongLyrics lyrics = SongLyrics.parse(cached.original(), cached.translated());
                    if (!lyrics.isEmpty()) return new Result(State.READY, lyrics);
                }
            }
        } catch (Exception invalidCache) { /* Retry the provider after a corrupt cache. */ }
        try {
            var raw = source.load(id);
            SongLyrics lyrics = SongLyrics.parse(raw.original(), raw.translated());
            if (!lyrics.isEmpty()) save(file, raw);
            return new Result(lyrics.isEmpty() ? State.EMPTY : State.READY, lyrics);
        } catch (Exception unavailable) { return new Result(State.ERROR, SongLyrics.EMPTY); }
    }

    private static SongLyrics local(Path audio) {
        String name = audio.getFileName().toString();
        int dot = name.lastIndexOf('.');
        Path sidecar = audio.resolveSibling((dot < 0 ? name : name.substring(0, dot)) + ".lrc");
        try { if (Files.isRegularFile(sidecar)) return SongLyrics.parse(Files.readString(sidecar), ""); }
        catch (Exception ignored) { }
        try {
            if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".mp3")) {
                var file = new Mp3File(audio.toFile());
                if (file.hasId3v2Tag()) return SongLyrics.parse(file.getId3v2Tag().getLyrics(), "");
            } else if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".flac")) {
                try (var input = Files.newInputStream(audio)) {
                    for (var metadata : new FLACDecoder(input).readMetadata()) if (metadata instanceof VorbisComment comments) {
                        for (String key : List.of("LYRICS", "UNSYNCEDLYRICS")) {
                            String[] values = comments.getCommentByName(key);
                            if (values != null && values.length > 0) return SongLyrics.parse(String.join("\n", values), "");
                        }
                    }
                }
            }
        } catch (Exception ignored) { }
        return SongLyrics.EMPTY;
    }

    private void save(Path file, NeteaseMusicApi.Lyrics lyrics) {
        Path partial = null;
        try {
            Files.createDirectories(cache);
            partial = Files.createTempFile(cache, ".lyrics-", ".tmp");
            Files.writeString(partial, GSON.toJson(lyrics));
            Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) { /* Successfully fetched lyrics remain available in memory. */ }
        finally { if (partial != null) try { Files.deleteIfExists(partial); } catch (Exception ignored) { } }
    }
}
