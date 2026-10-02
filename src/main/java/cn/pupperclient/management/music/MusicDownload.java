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
    private final NeteaseMusicApi api;
    private final MusicLibraryStore library;
    private final Path directory, cache;

    public MusicDownload(NeteaseMusicApi api, MusicLibraryStore library, Path directory, Path cache) {
        this.api = api;
        this.library = library;
        this.directory = directory.toAbsolutePath().normalize();
        this.cache = cache.toAbsolutePath().normalize();
    }

    public Result download(MusicTrack searched, String quality, String cookie, Path playing, IntConsumer progress)
            throws MusicError, IOException {
        MusicTrack track = searched;
        if (searched.coverUrl().isBlank()) try {
            List<MusicTrack> details = api.details(List.of(searched.id()));
            track = details.stream().filter(t -> t.id() == searched.id()).findFirst().orElse(searched);
        } catch (MusicError unavailable) {
            // Search metadata is sufficient: a detail outage must never turn a known title into music_ID.
        }
        if (track.title().isBlank()) throw new MusicError("music.error.metadata");
        Path existing = library.downloaded(track.id());
        if (existing == null) existing = legacy(track.id());
        if (existing != null) {
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
        NeteaseMusicApi.AudioSource source = api.audio(track.id(), quality, cookie);
        Files.createDirectories(directory);
        Path output = directory.resolve(filename(track, source.extension()));
        if (Files.exists(output)) throw new MusicError("music.error.fileexists");
        Path partial = Files.createTempFile(directory, ".music-download-", ".part");
        try {
            transfer(source.uri(), partial, progress);
            validateAudio(partial, source.extension());
            Files.move(partial, output);
            try {
                library.register(output.getFileName().toString(), track);
            } catch (IOException failedIndex) {
                Files.deleteIfExists(output);
                throw failedIndex;
            }
        } finally { Files.deleteIfExists(partial); }
        fetchCover(track);
        return new Result(output, track);
    }

    public Path legacy(long id) {
        for (String extension : List.of(".mp3", ".flac")) {
            Path path = directory.resolve("music_" + id + extension);
            if (Files.isRegularFile(path)) return path;
        }
        return null;
    }

    public Path cover(long id) { return cache.resolve("ncm-cover-" + id + ".jpg"); }

    public void fetchCover(MusicTrack track) {
        if (track.id() <= 0 || track.coverUrl().isBlank() || Files.exists(cover(track.id()))) return;
        Path partial = null;
        try {
            Files.createDirectories(cache);
            partial = Files.createTempFile(cache, ".cover-", ".tmp");
            URI url = URI.create(track.coverUrl() + (track.coverUrl().contains("?") ? "&" : "?") + "param=600y600");
            transfer(url, partial, _ -> { });
            if (ImageIO.read(partial.toFile()) != null)
                Files.move(partial, cover(track.id()), StandardCopyOption.REPLACE_EXISTING);
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
            long read = 0;
            int previous = -1;
            try (InputStream input = connection.getInputStream(); OutputStream stream = Files.newOutputStream(output)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    stream.write(buffer, 0, count);
                    read += count;
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
        int count = clean.codePointCount(0, clean.length());
        if (count > 110) clean = clean.substring(0, clean.offsetByCodePoints(0, 110));
        clean = clean.replaceAll("[. ]+$", "");
        if (clean.isBlank()) clean = "Track";
        return clean + " [" + track.id() + "]" + extension;
    }
}
