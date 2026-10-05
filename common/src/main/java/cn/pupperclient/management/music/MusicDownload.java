package cn.pupperclient.management.music;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

/** Downloads publish complete audio files and their metadata together, never partial library entries. */
public final class MusicDownload {
    public record Result(Path audio, MusicTrack track) { }
    private final MusicProvider api;
    private final MusicLibraryStore library;
    private final Path directory, cache;
    // A striped lock bounds memory while coordinating publication across providers/accounts.
    private static final Object[] PLAYBACK_LOCKS = java.util.stream.IntStream.range(0, 64)
        .mapToObj(_ -> new Object()).toArray();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final ScheduledExecutorService READ_TIMEOUTS = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().daemon().name("Pupper Client music HTTP timeout").factory());

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
        return download(searched, quality, cookie, () -> playing, progress, new MusicPreparation.Cancellation());
    }
    public Result download(MusicTrack searched, String quality, String cookie, Path playing, IntConsumer progress,
            MusicPreparation.Cancellation cancellation) throws MusicError, IOException {
        return download(searched, quality, cookie, () -> playing, progress, cancellation);
    }
    public Result download(MusicTrack searched, String quality, String cookie, Supplier<Path> playing, IntConsumer progress,
            MusicPreparation.Cancellation cancellation) throws MusicError, IOException {
        cancellation.check();
        MusicTrack track = searched;
        if (searched.coverUrl().isBlank()) try {
            track = api.track(searched.providerId(), cancellation);
        } catch (MusicError unavailable) {
            // Search metadata is sufficient: a detail outage must never turn a known title into music_ID.
        }
        cancellation.check();
        if (track.title().isBlank()) throw new MusicError("music.error.metadata");
        Path existing = library.downloaded(track);
        if (existing == null && track.provider().equals("netease")) existing = legacy(track.id());
        MusicTrack saved = existing == null ? null : library.metadata(existing.getFileName().toString());
        MusicProvider.AudioSource source = null;
        if (saved != null && saved.preview()) {
            source = api.audio(track, quality, cookie, true, cancellation);
            cancellation.check();
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
            if (!existing.equals(renamed) && !existing.equals(playing.get()) && !Files.exists(renamed)) {
                try {
                    Files.move(existing, renamed);
                    existing = renamed;
                } catch (IOException locked) { /* Keep the playable file and repair its sidecar below. */ }
            }
            library.register(existing.getFileName().toString(), track, previousFilename);
            fetchCover(track.coverUrl(), cover(track), track.provider(), cancellation);
            progress.accept(100);
            return new Result(existing, track);
        }
        if (!track.downloadable()) throw new MusicError("music.error.downloadrestricted");
        if (source == null) source = api.audio(track, quality, cookie, true, cancellation);
        cancellation.check();
        track = track.withAccess(source.fee(), source.previewMillis());
        if (replacePreview && existing.equals(playing.get())) throw new MusicError("music.error.previewplaying");
        Files.createDirectories(directory);
        Path output = directory.resolve(filename(track, source.extension()));
        if (Files.exists(output) && !output.equals(existing)) throw new MusicError("music.error.fileexists");
        Path partial = Files.createTempFile(directory, ".music-download-", ".part");
        try {
            transfer(source.uri(), partial, value -> progress.accept(Math.min(99, value)), cancellation);
            validateAudio(partial, source.extension());
            cancellation.check();
            synchronized (PLAYBACK_LOCKS[Math.floorMod(output.hashCode(), PLAYBACK_LOCKS.length)]) {
                cancellation.check();
                if (replacePreview && existing.equals(playing.get())) throw new MusicError("music.error.previewplaying");
                // Multiple callers can finish the same song; only one complete file is published.
                if (Files.isRegularFile(output) && !output.equals(existing)) {
                    validateAudio(output, source.extension());
                    MusicTrack published = library.metadata(output.getFileName().toString());
                    if (published != null) track = published;
                } else publish(partial, output, track, replacePreview ? existing : null);
            }
        } finally { Files.deleteIfExists(partial); }
        fetchCover(track.coverUrl(), cover(track), track.provider(), cancellation);
        progress.accept(100);
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
    public Path cover(MusicCollection collection) { return cache.resolve(collection.coverFilename()); }

    /** Temporary playback files never become library downloads or grant offline-download permission. */
    public Result playback(MusicTrack track, String quality, Path playing, IntConsumer progress) throws MusicError, IOException {
        return playback(track, quality, null, playing, progress);
    }
    public Result playback(MusicTrack track, String quality, String cookie, Path playing, IntConsumer progress) throws MusicError, IOException {
        return playback(track, quality, cookie, () -> playing, Set::of, progress, new MusicPreparation.Cancellation());
    }
    public Result playback(MusicTrack track, String quality, String cookie, Supplier<Path> playing,
            Supplier<Set<String>> protectedPrefixes, IntConsumer progress, MusicPreparation.Cancellation cancellation)
            throws MusicError, IOException {
        cancellation.check();
        if (!track.playable()) throw new MusicError("music.error.playrestricted");
        MusicProvider.AudioSource source = api.audio(track, quality, cookie, false, cancellation);
        cancellation.check();
        track = track.withAccess(source.fee(), source.previewMillis());
        Files.createDirectories(cache);
        String accessKey = source.previewMillis() == 0 ? "full" : "trial-" + source.previewMillis();
        Path output = cache.resolve(playbackPrefix(track, quality) + accessKey + source.extension());
        // Revalidate access before reusing a cached track, even when its bytes are already present.
        if (!Files.isRegularFile(output)) {
            Path partial = Files.createTempFile(cache, ".music-preview-", ".part");
            try {
                transfer(source.uri(), partial, progress, cancellation);
                validateAudio(partial, source.extension());
                cancellation.check();
                synchronized (PLAYBACK_LOCKS[Math.floorMod(output.hashCode(), PLAYBACK_LOCKS.length)]) {
                    cancellation.check();
                    // Different credentials can legitimately resolve to the same complete cache file.
                    if (!Files.isRegularFile(output)) Files.move(partial, output);
                    else validateAudio(output, source.extension());
                }
            } finally { Files.deleteIfExists(partial); }
        }
        cancellation.check();
        fetchCover(track.coverUrl(), cover(track), track.provider(), cancellation);
        cancellation.check();
        prunePlaybackCache(output, playing, protectedPrefixes);
        progress.accept(100);
        return new Result(output, track);
    }

    public static String playbackPrefix(MusicTrack track, String quality) {
        String qualityKey = java.util.HexFormat.of().formatHex(quality.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "music-preview-" + track.cacheId() + "-" + qualityKey + "-";
    }
    private void prunePlaybackCache(Path keep, Supplier<Path> playing, Supplier<Set<String>> protectedPrefixes) throws IOException {
        try (var entries = Files.list(cache)) {
            List<Path> files = entries.filter(p -> p.getFileName().toString().startsWith("music-preview-")
                && Files.isRegularFile(p) && !p.equals(keep)).sorted((a, b) -> {
                    try { return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a)); }
                    catch (IOException unavailable) { return 0; }
                }).toList();
            int retained = 0;
            for (Path file : files) {
                // Consult live playback state: a download's original playing-file snapshot can be stale.
                if (file.equals(playing.get()) || protectedPrefixes.get().stream()
                        .anyMatch(prefix -> file.getFileName().toString().startsWith(prefix))) continue;
                if (retained++ >= 6) try { Files.deleteIfExists(file); } catch (IOException locked) { }
            }
        }
    }

    public void fetchCover(MusicTrack track) {
        if (track.remote()) fetchCover(track.coverUrl(), cover(track), track.provider());
    }
    public void fetchCover(MusicCollection collection) { fetchCover(collection.coverUrl(), cover(collection), collection.provider()); }
    private void fetchCover(String url, Path output, String provider) {
        fetchCover(url, output, provider, new MusicPreparation.Cancellation());
    }
    private void fetchCover(String url, Path output, String provider, MusicPreparation.Cancellation cancellation) {
        if (url.isBlank() || Files.exists(output)) return;
        Path partial = null;
        try {
            Files.createDirectories(cache);
            partial = Files.createTempFile(cache, ".cover-", ".tmp");
            URI uri = URI.create(url + (provider.equals("netease")
                ? (url.contains("?") ? "&" : "?") + "param=600y600" : ""));
            transfer(uri, partial, _ -> { }, cancellation);
            if (ImageIO.read(partial.toFile()) != null)
                Files.move(partial, output, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | MusicError | RuntimeException unavailable) {
            // Cover failure does not discard successfully downloaded audio.
        } finally {
            if (partial != null) try { Files.deleteIfExists(partial); } catch (IOException ignored) { }
        }
    }

    private static void transfer(URI url, Path output, IntConsumer progress) throws IOException, MusicError {
        transfer(url, output, progress, new MusicPreparation.Cancellation());
    }
    private static void transfer(URI url, Path output, IntConsumer progress, MusicPreparation.Cancellation cancellation)
            throws IOException, MusicError {
        if (!"https".equalsIgnoreCase(url.getScheme()) && !"http".equalsIgnoreCase(url.getScheme()))
            throw new IOException("Unsupported music URL scheme");
        HttpRequest request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Pupper Client").GET().build();
        var pending = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var requestCancellation = cancellation.onCancel(() -> pending.cancel(true))) {
            cancellation.check();
            HttpResponse<InputStream> response;
            try { response = pending.get(); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Music transfer interrupted");
            } catch (CancellationException cancelled) {
                throw new InterruptedIOException("Music transfer cancelled");
            } catch (ExecutionException failure) {
                if (failure.getCause() instanceof IOException io) throw io;
                throw new IOException("Music transfer failed", failure.getCause());
            }
            // HttpClient's response stream can be closed without waiting for its blocked read.
            // HttpURLConnection.disconnect instead holds the reader's lock, freezing callers.
            try (InputStream input = response.body();
                 var bodyCancellation = cancellation.onCancel(() -> closeBody(input))) {
                cancellation.check();
                if (response.statusCode() != 200) throw new MusicError("music.error.network");
                long length = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (length > 512L * 1024 * 1024) throw new MusicError("music.error.file");
                AtomicLong lastRead = new AtomicLong(System.nanoTime());
                AtomicBoolean timedOut = new AtomicBoolean();
                // ofInputStream completes on headers, so the request timeout alone cannot bound body reads.
                var timeout = READ_TIMEOUTS.scheduleAtFixedRate(() -> {
                    if (System.nanoTime() - lastRead.get() >= TimeUnit.SECONDS.toNanos(30)) {
                        timedOut.set(true); closeBody(input);
                    }
                }, 30, 1, TimeUnit.SECONDS);
                long read = 0;
                int previous = -1;
                try (OutputStream stream = Files.newOutputStream(output)) {
                    byte[] buffer = new byte[32 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        cancellation.check();
                        if (timedOut.get()) throw new IOException("Music response timed out");
                        lastRead.set(System.nanoTime());
                        stream.write(buffer, 0, count);
                        read += count;
                        if (read > 512L * 1024 * 1024) throw new MusicError("music.error.file");
                        int percent = length > 0 ? (int) Math.min(99, read * 100 / length) : 0;
                        if (percent != previous) { progress.accept(percent); previous = percent; }
                    }
                } finally { timeout.cancel(false); }
                cancellation.check();
                if (timedOut.get()) throw new IOException("Music response timed out");
                if (read == 0 || (length > 0 && read != length)) throw new MusicError("music.error.network");
                progress.accept(100);
            }
        } finally {
            // Headers may finish just as get() is interrupted; close even an unclaimed response body.
            pending.whenComplete((response, _) -> { if (response != null) closeBody(response.body()); });
            pending.cancel(true);
        }
    }

    private static void closeBody(InputStream input) {
        try { input.close(); } catch (IOException ignored) { }
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
