package cn.pupperclient.management.music;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.IntConsumer;
import javax.imageio.ImageIO;

/** Downloads publish complete audio files and their metadata together, never partial library entries. */
public final class MusicDownload {
    public record Result(Path audio, MusicTrack track) { }
    private final MusicProvider api;
    private final MusicLibraryStore library;
    private final Path directory, cache;

    public MusicDownload(NeteaseMusicApi api, MusicLibraryStore library, Path directory, Path cache) {
        this(new NeteaseMusicProvider(api), library, directory, cache);
    }

    public MusicDownload(MusicProvider api, MusicLibraryStore library, Path directory, Path cache) {
        this.api = api;
        this.library = library;
        this.directory = directory.toAbsolutePath().normalize();
        this.cache = cache.toAbsolutePath().normalize();
    }

    public Result download(MusicTrack searched, String quality, String cookie, Path playing, IntConsumer progress)
            throws MusicError, IOException {
        MusicTrack track = searched;
        if (searched.coverUrl().isBlank()) try {
            track = api.track(searched.providerId());
        } catch (MusicError unavailable) {
            // Search metadata is sufficient: a detail outage must never turn a known title into music_ID.
        }
        if (track.title().isBlank()) throw new MusicError("music.error.metadata");
        Path existing = library.downloaded(track);
        if (existing == null && track.provider().equals("netease")) existing = legacy(track.id());
        MusicTrack saved = existing == null ? null : library.metadata(existing.getFileName().toString());
        MusicProvider.AudioSource source = null;
        if (saved != null && saved.preview()) {
            source = api.audio(track, quality, cookie, true);
            track = track.withAccess(source.fee(), source.previewMillis());
        }
        boolean replacePreview = saved != null && saved.preview() && source != null && saved.previewMillis() != source.previewMillis();
        if (existing != null && !replacePreview) {
            if (saved != null) track = track.withAccess(track.fee(), saved.previewMillis());
            String previousFilename = existing.getFileName().toString();
            String extension = existing.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".flac")
                ? ".flac" : ".mp3";
            Path renamed = directory.resolve(filename(track, extension));
            // Windows locks actively playing files. Their displayed metadata can still be repaired immediately.
            if (!existing.equals(renamed) && !existing.equals(playing) && !Files.exists(renamed)) {
                try {
                    Files.move(existing, renamed);
                    existing = renamed;
                } catch (IOException locked) { /* Keep the playable file and repair its sidecar below. */ }
            }
            library.register(existing.getFileName().toString(), track, previousFilename);
            fetchCover(track);
            progress.accept(100);
            return new Result(existing, track);
        }
        if (!track.downloadable()) throw new MusicError("music.error.downloadrestricted");
        if (source == null) source = api.audio(track, quality, cookie, true);
        track = track.withAccess(source.fee(), source.previewMillis());
        if (replacePreview && existing.equals(playing)) throw new MusicError("music.error.previewplaying");
        Files.createDirectories(directory);
        Path output = directory.resolve(filename(track, source.extension()));
        if (Files.exists(output) && !output.equals(existing)) throw new MusicError("music.error.fileexists");
        Path partial = Files.createTempFile(directory, ".music-download-", ".part");
        try {
            transfer(source.uri(), partial, progress);
            validateAudio(partial, source.extension());
            publish(partial, output, track, replacePreview ? existing : null);
        } finally { Files.deleteIfExists(partial); }
        fetchCover(track);
        return new Result(output, track);
    }

    private void publish(Path partial, Path output, MusicTrack track, Path previous) throws IOException {
        Path backup = null;
        boolean published = false;
        try {
            if (previous != null) {
                Path temporary = Files.createTempFile(directory, ".music-replaced-", ".tmp");
                try { Files.move(previous, temporary, StandardCopyOption.REPLACE_EXISTING); backup = temporary; }
                catch (IOException failure) { Files.deleteIfExists(temporary); throw failure; }
            }
            Files.move(partial, output); published = true;
            library.register(output.getFileName().toString(), track, previous == null ? output.getFileName().toString() : previous.getFileName().toString());
        } catch (IOException failure) {
            try {
                if (published) Files.deleteIfExists(output);
                if (backup != null) Files.move(backup, previous, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException restore) { failure.addSuppressed(restore); }
            throw failure;
        }
        if (backup != null) Files.deleteIfExists(backup);
    }

    public Path legacy(long id) {
        for (String extension : List.of(".mp3", ".flac")) {
            Path path = directory.resolve("music_" + id + extension);
            if (Files.isRegularFile(path)) return path;
        }
        return null;
    }

    public Path cover(long id) { return cache.resolve("ncm-cover-" + id + ".jpg"); }
    public Path cover(MusicTrack track) { return cache.resolve(track.coverFilename()); }

    /** Temporary playback files never become library downloads or grant offline-download permission. */
    public Result playback(MusicTrack track, String quality, Path playing, IntConsumer progress) throws MusicError, IOException {
        return playback(track, quality, null, playing, progress);
    }
    public Result playback(MusicTrack track, String quality, String cookie, Path playing, IntConsumer progress) throws MusicError, IOException {
        if (!track.playable()) throw new MusicError("music.error.playrestricted");
        MusicProvider.AudioSource source = api.audio(track, quality, cookie, false);
        track = track.withAccess(source.fee(), source.previewMillis());
        Files.createDirectories(cache);
        String qualityKey = java.util.HexFormat.of().formatHex(quality.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String accessKey = source.previewMillis() == 0 ? "full" : "trial-" + source.previewMillis();
        Path output = cache.resolve("music-preview-" + track.cacheId() + "-" + qualityKey + "-" + accessKey + source.extension());
        // Revalidate access before reusing a cached track, even when its bytes are already present.
        if (!Files.isRegularFile(output)) {
            Path partial = Files.createTempFile(cache, ".music-preview-", ".part");
            try {
                transfer(source.uri(), partial, progress);
                validateAudio(partial, source.extension());
                Files.move(partial, output);
            } finally { Files.deleteIfExists(partial); }
        }
        fetchCover(track);
        prunePlaybackCache(output, playing);
        progress.accept(100);
        return new Result(output, track);
    }

    private void prunePlaybackCache(Path keep, Path playing) throws IOException {
        try (var entries = Files.list(cache)) {
            List<Path> files = entries.filter(p -> p.getFileName().toString().startsWith("music-preview-")
                && Files.isRegularFile(p) && !p.equals(keep) && !p.equals(playing)).sorted((a, b) -> {
                    try { return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a)); }
                    catch (IOException unavailable) { return 0; }
                }).toList();
            for (int i = 6; i < files.size(); i++) try { Files.deleteIfExists(files.get(i)); } catch (IOException locked) { }
        }
    }

    public void fetchCover(MusicTrack track) {
        if (!track.remote() || track.coverUrl().isBlank() || Files.exists(cover(track))) return;
        Path partial = null;
        try {
            Files.createDirectories(cache);
            partial = Files.createTempFile(cache, ".cover-", ".tmp");
            URI url = URI.create(track.coverUrl() + (track.provider().equals("netease")
                ? (track.coverUrl().contains("?") ? "&" : "?") + "param=600y600" : ""));
            transfer(url, partial, _ -> { });
            if (ImageIO.read(partial.toFile()) != null)
                Files.move(partial, cover(track), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | MusicError | RuntimeException unavailable) {
            // Cover failure does not discard successfully downloaded audio.
        } finally {
            if (partial != null) try { Files.deleteIfExists(partial); } catch (IOException ignored) { }
        }
    }

    private static void transfer(URI url, Path output, IntConsumer progress) throws IOException, MusicError {
        HttpURLConnection connection = NeteaseMusicApi.open(url);
        try {
            if (connection.getResponseCode() != 200) throw new MusicError("music.error.network");
            long length = connection.getContentLengthLong();
            if (length > 512L * 1024 * 1024) throw new MusicError("music.error.file");
            long read = 0;
            int previous = -1;
            try (InputStream input = connection.getInputStream(); OutputStream stream = Files.newOutputStream(output)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    stream.write(buffer, 0, count);
                    read += count;
                    if (read > 512L * 1024 * 1024) throw new MusicError("music.error.file");
                    int percent = length > 0 ? (int) Math.min(99, read * 100 / length) : 0;
                    if (percent != previous) { progress.accept(percent); previous = percent; }
                }
            }
            if (read == 0 || (length > 0 && read != length)) throw new MusicError("music.error.network");
            progress.accept(100);
        } finally { connection.disconnect(); }
    }

    private static void validateAudio(Path path, String extension) throws IOException, MusicError {
        byte[] header;
        try (InputStream stream = Files.newInputStream(path)) { header = stream.readNBytes(4); }
        boolean flac = header.length == 4 && header[0] == 'f' && header[1] == 'L' && header[2] == 'a' && header[3] == 'C';
        boolean mp3 = header.length >= 3 && ((header[0] == 'I' && header[1] == 'D' && header[2] == '3')
            || ((header[0] & 0xff) == 0xff && (header[1] & 0xe0) == 0xe0));
        if (extension.equals(".flac") ? !flac : !mp3) throw new MusicError("music.error.format");
    }

    public static String filename(MusicTrack track, String extension) {
        String display = track.title() + (track.artist().isBlank() ? "" : " - " + track.artist());
        String clean = display.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        String suffix = " [" + (track.provider().equals("netease") ? track.providerId() : track.cacheId()) + "]" + extension;
        int limit = Math.max(8, Math.min(110, 240 - suffix.length()));
        if (clean.length() > limit) {
            int end = Character.isHighSurrogate(clean.charAt(limit - 1)) ? limit - 1 : limit;
            clean = clean.substring(0, end);
        }
        clean = clean.replaceAll("[. ]+$", "");
        if (clean.isBlank()) clean = "Track";
        return clean + suffix;
    }
}
