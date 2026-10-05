package cn.pupperclient.music;

import cn.pupperclient.management.music.MusicDownload;
import cn.pupperclient.management.music.MusicError;
import cn.pupperclient.management.music.MusicLibraryStore;
import cn.pupperclient.management.music.MusicPreparation;
import cn.pupperclient.management.music.MusicTrack;
import cn.pupperclient.management.music.NeteaseMusicApi;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises complete-file prefetch with real loopback HTTP, cancellation and filesystem publication. */
public final class MusicDownloadPrefetchChecks {
    private static final int TIMEOUT_SECONDS = 8;
    private static int checks;

    private MusicDownloadPrefetchChecks() { }

    public static void run() throws Exception {
        checks = 0;
        Path root = Files.createTempDirectory("pupper-prefetch-checks-");
        try (Fixture fixture = new Fixture()) {
            joinedForeground(root.resolve("joined"), fixture);
            cancelledTransfer(root.resolve("cancelled"), fixture);
            cancelledResponseHeaders(root.resolve("cancelled-headers"), fixture);
            cancelledForeground(root.resolve("cancelled-foreground"), fixture);
            replacedForegroundJoins(root.resolve("replaced-foreground"), fixture);
            redirectsAndChunkedResponses(root.resolve("http-behavior"), fixture);
            invalidLengths(root.resolve("invalid-lengths"), fixture);
            completedPrefetchRevalidates(root.resolve("revalidated"), fixture);
            accessAndQualityIsolation(root.resolve("access"), fixture);
            liveCacheProtection(root.resolve("protected"), fixture);
            concurrentPublication(root.resolve("publication"), fixture);
            System.out.println("Music download prefetch checks passed: " + checks + " assertions; in-flight joining, "
                + "header/body/subscriber cancellation, same-track subscriber replacement, redirects, "
                + "chunked/truncated responses, size limits, access revalidation, "
                + "VIP/quality isolation, live cache protection "
                + "and concurrent complete-file publication.");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void joinedForeground(Path root, Fixture fixture) throws Exception {
        Context context = new Context(root, fixture);
        MusicTrack track = track(101);
        Gate gate = fixture.gate(track.id(), 1);
        CountDownLatch reading = new CountDownLatch(1);
        try (ExecutorService worker = serialWorker();
             MusicPreparation<MusicDownload.Result> preparation = new MusicPreparation<>(worker, Runnable::run)) {
            var key = key(track, "standard", "guest-fingerprint");
            preparation.prefetch(key, token -> context.download.playback(track, "standard", "fixture-guest",
                () -> null, Set::of, _ -> reading.countDown(), token));
            await(reading, "Prefetch did not begin reading the slow response");
            require(cacheFiles(context.cache).stream().noneMatch(path -> path.getFileName().toString().endsWith(".mp3")),
                "A slow prefetch published incomplete audio");
            CompletableFuture<MusicDownload.Result> first = prepare(preparation, key,
                token -> context.playback(track, "standard", "fixture-guest", token));
            CompletableFuture<MusicDownload.Result> second = prepare(preparation, key,
                token -> context.playback(track, "standard", "fixture-guest", token));
            require(!first.isDone() && !second.isDone(), "Foreground consumed an incomplete prefetch");
            gate.release.countDown();
            var result = get(first);
            require(get(second).audio().equals(result.audio()), "Joined listeners received different audio files");
            require(fixture.apiCalls(track.id()) == 1 && fixture.mediaCalls(track.id()) == 1,
                "Foreground failed to join the single in-flight audio transfer");
            require(Arrays.equals(Files.readAllBytes(result.audio()), Fixture.audio("full", "standard")),
                "Joined foreground did not receive the complete response bytes");
            cleanTemporaryPlayback(context, track);
        } finally { gate.release.countDown(); }
    }

    private static void cancelledTransfer(Path root, Fixture fixture) throws Exception {
        Context context = new Context(root, fixture);
        MusicTrack track = track(102);
        Gate gate = fixture.gate(track.id(), 1);
        CountDownLatch reading = new CountDownLatch(1), stopped = new CountDownLatch(1);
        try (ExecutorService worker = serialWorker(); ExecutorService control = Executors.newVirtualThreadPerTaskExecutor();
             MusicPreparation<MusicDownload.Result> preparation = new MusicPreparation<>(worker, Runnable::run)) {
            preparation.prefetch(key(track, "standard", "guest-fingerprint"), token -> {
                try {
                    return context.download.playback(track, "standard", "fixture-guest", () -> null, Set::of,
                        _ -> reading.countDown(), token);
                } finally { stopped.countDown(); }
            });
            await(reading, "Cancellation fixture never reached a blocked body read");
            var cancellation = control.submit(preparation::cancelPrefetch);
            try {
                cancellation.get(3, TimeUnit.SECONDS);
                require(stopped.await(3, TimeUnit.SECONDS), "Cancellation did not release the blocked audio transfer");
                require(cacheFiles(context.cache).isEmpty(), "Cancelled transfer published audio or leaked its partial");
                cleanTemporaryPlayback(context, track);
            } finally {
                // Always release the server, including on a regression, so the check itself cannot hang cleanup.
                gate.release.countDown();
            }
        } finally { gate.release.countDown(); }
    }

    private static void completedPrefetchRevalidates(Path root, Fixture fixture) throws Exception {
        Context context = new Context(root, fixture);
        MusicTrack track = track(103);
        try (ExecutorService worker = serialWorker();
             MusicPreparation<MusicDownload.Result> preparation = new MusicPreparation<>(worker, Runnable::run)) {
            var key = key(track, "standard", "vip-fingerprint");
            preparation.prefetch(key, token -> context.playback(track, "standard", "fixture-vip-a", token));
            drain(worker);
            require(fixture.apiCalls(track.id()) == 1 && fixture.mediaCalls(track.id()) == 1,
                "Completed speculative preparation did not fetch exactly one audio source");
            var result = get(prepare(preparation, key,
                token -> context.playback(track, "standard", "fixture-vip-a", token)));
            require(fixture.apiCalls(track.id()) == 2 && fixture.mediaCalls(track.id()) == 1,
                "Completed prefetch bypassed access revalidation or downloaded complete bytes again");
            require(!result.track().preview(), "A verified full result acquired a preview label");
            fixture.revoked.add(track.id());
            Throwable revoked = failure(prepare(preparation, key,
                token -> context.playback(track, "standard", "fixture-vip-a", token)));
            require(revoked instanceof MusicError error && error.key().equals("music.error.unavailable"),
                "Completed in-memory results bypassed the provider's revoked playback access");
            require(fixture.apiCalls(track.id()) == 3 && fixture.mediaCalls(track.id()) == 1,
                "Revoked access attempted another media transfer or skipped the provider");
            require(Files.isRegularFile(result.audio()), "Access revalidation damaged an existing complete cache file");
            cleanTemporaryPlayback(context, track);
        } finally { fixture.revoked.remove(track.id()); }
    }

    private static void cancelledResponseHeaders(Path root, Fixture fixture) throws Exception {
        Context context = new Context(root, fixture);
        MusicTrack track = track(107);
        Gate gate = fixture.gate(track.id(), 1);
        fixture.blockedHeaders.add(track.id());
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicInteger progressNotifications = new AtomicInteger();
        try (ExecutorService worker = serialWorker(); ExecutorService control = Executors.newVirtualThreadPerTaskExecutor();
             MusicPreparation<MusicDownload.Result> preparation = new MusicPreparation<>(worker, Runnable::run)) {
            preparation.prefetch(key(track, "standard", "guest-fingerprint"), token -> {
                try {
                    return context.download.playback(track, "standard", "fixture-guest", () -> null, Set::of,
                        _ -> progressNotifications.incrementAndGet(), token);
                } finally { stopped.countDown(); }
            });
            await(gate.started, "Header cancellation fixture never received the media request");
            var cancellation = control.submit(preparation::cancelPrefetch);
            try {
                cancellation.get(3, TimeUnit.SECONDS);
                require(stopped.await(3, TimeUnit.SECONDS), "Cancellation did not release a pending response-header wait");
                require(progressNotifications.get() == 0, "Header cancellation fixture unexpectedly transferred audio first");
                require(cacheFiles(context.cache).isEmpty(), "Header cancellation published audio or leaked a partial");
                cleanTemporaryPlayback(context, track);
            } finally { gate.release.countDown(); }
        } finally {
            gate.release.countDown();
            fixture.blockedHeaders.remove(track.id());
        }
    }

    private static void redirectsAndChunkedResponses(Path root, Fixture fixture) throws Exception {
        Context context = new Context(root, fixture);
        MusicTrack redirected = track(108), chunked = track(109);
        fixture.redirects.add(redirected.id());
        fixture.chunked.add(chunked.id());
        try {
            var result = context.playback(redirected, "standard", "fixture-guest", new MusicPreparation.Cancellation());
            require(fixture.redirectCalls(redirected.id()) == 1 && fixture.mediaCalls(redirected.id()) == 1,
                "A relative CDN redirect was not followed to the final audio response");
            require(Arrays.equals(Files.readAllBytes(result.audio()), Fixture.audio("full", "standard")),
                "A redirect response published its empty body instead of the final audio");
            require("Pupper Client".equals(fixture.userAgents.get(redirected.id())),
                "The redirected media request lost the music client's User-Agent");
            var chunkedResult = context.playback(chunked, "standard", "fixture-guest", new MusicPreparation.Cancellation());
            require(Arrays.equals(Files.readAllBytes(chunkedResult.audio()), Fixture.audio("full", "standard")),
                "An audio response without Content-Length was rejected or published incomplete chunked data");
            require(fixture.apiCalls(chunked.id()) == 1 && fixture.mediaCalls(chunked.id()) == 1,
                "A chunked response caused a duplicate media request");
            cleanTemporaryPlayback(context, redirected);
            cleanTemporaryPlayback(context, chunked);
        } finally {
            fixture.redirects.remove(redirected.id());
            fixture.chunked.remove(chunked.id());
        }
    }

    private static void cancelledForeground(Path root, Fixture fixture) throws Exception {
        Context context = new Context(root, fixture);
        MusicTrack track = track(112);
        Gate gate = fixture.gate(track.id(), 1);
        CountDownLatch reading = new CountDownLatch(1), stopped = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        try (ExecutorService worker = serialWorker(); ExecutorService control = Executors.newVirtualThreadPerTaskExecutor();
             MusicPreparation<MusicDownload.Result> preparation = new MusicPreparation<>(worker, Runnable::run)) {
            try (MusicPreparation.Registration subscription = preparation.prepare(key(track, "standard", "guest-fingerprint"),
                    token -> {
                        try {
                            return context.download.playback(track, "standard", "fixture-guest", () -> null, Set::of,
                                _ -> reading.countDown(), token);
                        } finally { stopped.countDown(); }
                    }, _ -> callbacks.incrementAndGet(), _ -> callbacks.incrementAndGet())) {
                await(reading, "Foreground unsubscribe fixture never reached its blocked body read");
                var cancellation = control.submit(subscription::close);
                try {
                    cancellation.get(3, TimeUnit.SECONDS);
                    require(stopped.await(3, TimeUnit.SECONDS), "Last foreground unsubscribe did not release the blocked transfer");
                    require(cacheFiles(context.cache).isEmpty(), "An unsubscribed foreground download published audio or leaked a partial");
                    require(callbacks.get() == 0, "A closed foreground subscription still received success or failure");
                    cleanTemporaryPlayback(context, track);
                } finally { gate.release.countDown(); }
            }
        } finally { gate.release.countDown(); }
    }

    private static void replacedForegroundJoins(Path root, Fixture fixture) throws Exception {
        Context context = new Context(root, fixture);
        MusicTrack track = track(113);
        Gate gate = fixture.gate(track.id(), 1);
        CountDownLatch reading = new CountDownLatch(1);
        AtomicInteger oldCallbacks = new AtomicInteger();
        CompletableFuture<MusicDownload.Result> result = new CompletableFuture<>();
        try (ExecutorService worker = serialWorker();
             MusicPreparation<MusicDownload.Result> preparation = new MusicPreparation<>(worker, Runnable::run)) {
            var key = key(track, "standard", "guest-fingerprint");
            try (MusicPreparation.Registration previous = preparation.prepare(key,
                    token -> context.download.playback(track, "standard", "fixture-guest", () -> null, Set::of,
                        _ -> reading.countDown(), token),
                    _ -> oldCallbacks.incrementAndGet(), _ -> oldCallbacks.incrementAndGet())) {
                await(reading, "Same-track subscription fixture did not begin its slow transfer");
                try (MusicPreparation.Registration next = preparation.prepare(key,
                        token -> context.playback(track, "standard", "fixture-guest", token),
                        result::complete, result::completeExceptionally)) {
                    // The newly selected subscription owns the request before the old selection lets go.
                    previous.close();
                    gate.release.countDown();
                    var prepared = get(result);
                    require(fixture.apiCalls(track.id()) == 1 && fixture.mediaCalls(track.id()) == 1,
                        "Replacing a same-track foreground subscription restarted its audio transfer");
                    require(oldCallbacks.get() == 0, "The replaced foreground selection received an obsolete callback");
                    require(Arrays.equals(Files.readAllBytes(prepared.audio()), Fixture.audio("full", "standard")),
                        "Replacing a same-track subscription cancelled or truncated the surviving transfer");
                    cleanTemporaryPlayback(context, track);
                }
            }
        } finally { gate.release.countDown(); }
    }

    private static void invalidLengths(Path root, Fixture fixture) throws Exception {
        Context oversizedContext = new Context(root.resolve("oversized"), fixture);
        Context truncatedContext = new Context(root.resolve("truncated"), fixture);
        MusicTrack oversized = track(110), truncated = track(111);
        Gate oversizedGate = fixture.gate(oversized.id(), 1);
        fixture.oversized.add(oversized.id());
        fixture.truncated.add(truncated.id());
        try (ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
             MusicPreparation<MusicDownload.Result> preparation = new MusicPreparation<>(worker, Runnable::run)) {
            CompletableFuture<MusicDownload.Result> oversizedResult = prepare(preparation,
                key(oversized, "standard", "guest-fingerprint"),
                token -> oversizedContext.playback(oversized, "standard", "fixture-guest", token));
            await(oversizedGate.started, "Oversized response headers were not flushed to the client");
            Throwable largeFailure = failure(oversizedResult);
            require(largeFailure instanceof MusicError error && error.key().equals("music.error.file"),
                "A declared response larger than 512 MiB was not rejected by the size limit: "
                    + largeFailure.getClass().getName() + ": " + largeFailure.getMessage());
            require(fixture.bodyWrites.getOrDefault(oversized.id(), new AtomicInteger()).get() == 0,
                "The oversized-header fixture accidentally transferred a large response body");
            require(cacheFiles(oversizedContext.cache).isEmpty(), "Oversized audio published a cache file or leaked its partial");
            cleanTemporaryPlayback(oversizedContext, oversized);

            Throwable shortFailure = failure(prepare(preparation, key(truncated, "standard", "guest-fingerprint"),
                token -> truncatedContext.playback(truncated, "standard", "fixture-guest", token)));
            require(shortFailure instanceof IOException || shortFailure instanceof MusicError error
                    && error.key().equals("music.error.network"),
                "A truncated fixed-length response was not reported as an incomplete network transfer");
            require(fixture.mediaCalls(truncated.id()) == 1 && cacheFiles(truncatedContext.cache).isEmpty(),
                "A truncated response was retried unexpectedly or published incomplete audio");
            cleanTemporaryPlayback(truncatedContext, truncated);
        } finally {
            oversizedGate.release.countDown();
            fixture.oversized.remove(oversized.id());
            fixture.truncated.remove(truncated.id());
        }
    }

    private static void accessAndQualityIsolation(Path root, Fixture fixture) throws Exception {
        Context context = new Context(root, fixture);
        MusicTrack track = track(104);
        var preview = context.playback(track, "standard", "fixture-preview", new MusicPreparation.Cancellation());
        require(preview.track().fee() == 1 && preview.track().previewMillis() == 30_000,
            "Preloaded VIP preview lost its actual access metadata");
        require(Arrays.equals(Files.readAllBytes(preview.audio()), Fixture.audio("trial", "standard")),
            "Preview bytes were not the provider's preview response");
        var full = context.playback(track, "standard", "fixture-vip-a", new MusicPreparation.Cancellation());
        require(!full.track().preview() && !preview.audio().equals(full.audio()), "VIP full audio reused the trial cache");
        var guest = context.playback(track, "standard", "fixture-preview", new MusicPreparation.Cancellation());
        require(guest.track().preview() && guest.audio().equals(preview.audio()),
            "A guest selected another account's cached full audio");
        var secondVip = context.playback(track, "standard", "fixture-vip-b", new MusicPreparation.Cancellation());
        require(secondVip.audio().equals(full.audio()) && !secondVip.track().preview(),
            "A second authorized account could not reuse verified complete full audio");
        require(fixture.apiCalls(track.id()) == 4 && fixture.mediaCalls(track.id()) == 2,
            "Account changes did not revalidate access or unnecessarily redownloaded the same permitted audio");
        var lossless = context.playback(track, "lossless", "fixture-vip-b", new MusicPreparation.Cancellation());
        require(!lossless.audio().equals(full.audio()) && lossless.audio().toString().endsWith(".flac")
                && Arrays.equals(Files.readAllBytes(lossless.audio()), Fixture.audio("full", "lossless")),
            "A quality or decoder-format change reused MP3 cache bytes");
        var unknown = context.playback(track, "standard", "fixture-unknown", new MusicPreparation.Cancellation());
        require(unknown.track().preview() && unknown.track().previewMillis() == -1
                && !unknown.audio().equals(full.audio()) && !unknown.audio().equals(preview.audio()),
            "A preview with omitted duration became full audio or a guessed 30-second trial");
        fixture.revoked.add(track.id());
        try {
            context.playback(track, "standard", "fixture-vip-b", new MusicPreparation.Cancellation());
            throw new AssertionError("Revoked account reused a previously authorized complete cache");
        } catch (MusicError error) {
            require(error.key().equals("music.error.unavailable"), "Account access failure was misclassified");
        } finally { fixture.revoked.remove(track.id()); }
        cleanTemporaryPlayback(context, track);
    }

    private static void liveCacheProtection(Path root, Fixture fixture) throws Exception {
        Context context = new Context(root, fixture);
        Files.createDirectories(context.cache);
        MusicTrack track = track(105), selected = track(205);
        long baseTime = 1_600_000_000_000L;
        Path originallyPlaying = seed(context.cache, MusicDownload.playbackPrefix(track(305), "standard") + "full.mp3", baseTime);
        Path nowPlaying = seed(context.cache, MusicDownload.playbackPrefix(track(405), "standard") + "full.mp3", baseTime - 1000);
        Path selectedFile = seed(context.cache, MusicDownload.playbackPrefix(selected, "standard") + "full.mp3", baseTime - 2000);
        Path obsolete = seed(context.cache, "music-preview-obsolete.mp3", baseTime - 3000);
        for (int i = 0; i < 6; i++) seed(context.cache, "music-preview-recent-" + i + ".mp3", baseTime + (i + 1) * 1000);
        AtomicReference<Path> playing = new AtomicReference<>(originallyPlaying);
        AtomicReference<Set<String>> protectedPrefixes = new AtomicReference<>(Set.of());
        Gate gate = fixture.gate(track.id(), 1);
        CountDownLatch reading = new CountDownLatch(1);
        try (ExecutorService worker = serialWorker();
             MusicPreparation<MusicDownload.Result> preparation = new MusicPreparation<>(worker, Runnable::run)) {
            CompletableFuture<MusicDownload.Result> result = prepare(preparation, key(track, "standard", "guest-fingerprint"),
                token -> context.download.playback(track, "standard", "fixture-guest", playing::get,
                    protectedPrefixes::get, _ -> reading.countDown(), token));
            await(reading, "Cache protection fixture did not begin downloading");
            playing.set(nowPlaying);
            protectedPrefixes.set(Set.of(MusicDownload.playbackPrefix(selected, "standard")));
            gate.release.countDown();
            require(Files.isRegularFile(get(result).audio()), "Pruning removed the newly prepared complete audio");
            require(Files.isRegularFile(nowPlaying), "Pruning used the old playing-file snapshot after a track change");
            require(Files.isRegularFile(selectedFile), "Pruning removed the selected next-track cache");
            require(!Files.exists(originallyPlaying) && !Files.exists(obsolete),
                "Playback protection prevented normal eviction of unrelated old audio");
            require(cacheFiles(context.cache).size() == 9, "Cache pruning retained unrelated stale audio or lost protected files");
            cleanTemporaryPlayback(context, track);
        } finally { gate.release.countDown(); }
    }

    private static void concurrentPublication(Path root, Fixture fixture) throws Exception {
        Context context = new Context(root, fixture);
        MusicTrack track = track(106);
        Gate gate = fixture.gate(track.id(), 2);
        try (ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
             MusicPreparation<MusicDownload.Result> preparation = new MusicPreparation<>(worker, Runnable::run)) {
            CompletableFuture<MusicDownload.Result> first = prepare(preparation, key(track, "standard", "account-a-fingerprint"),
                token -> context.playback(track, "standard", "fixture-vip-a", token));
            CompletableFuture<MusicDownload.Result> second = prepare(preparation, key(track, "standard", "account-b-fingerprint"),
                token -> context.playback(track, "standard", "fixture-vip-b", token));
            await(gate.started, "Distinct accounts did not both begin their independently authorized transfers");
            gate.release.countDown();
            var firstResult = get(first);
            require(firstResult.audio().equals(get(second).audio()), "Concurrent permissions published duplicate cache identities");
            require(fixture.apiCalls(track.id()) == 2 && fixture.mediaCalls(track.id()) == 2,
                "Distinct credential contexts adopted another account's in-flight permissions");
            require(Arrays.equals(Files.readAllBytes(firstResult.audio()), Fixture.audio("full", "standard")),
                "Concurrent publication overwrote complete audio with partial bytes");
            require(cacheFiles(context.cache).size() == 1, "Concurrent publication leaked a partial or duplicate audio file");
            cleanTemporaryPlayback(context, track);
        } finally { gate.release.countDown(); }
    }

    private static MusicTrack track(long id) {
        return new MusicTrack(id, "Prefetch fixture " + id, "Artist", "Album", "", 120_000,
            "netease", Long.toString(id), true, false, 1, 0);
    }

    private static MusicPreparation.Key key(MusicTrack track, String quality, String credentialFingerprint) {
        return new MusicPreparation.Key(track.key(), quality, credentialFingerprint);
    }

    private static ExecutorService serialWorker() {
        return Executors.newSingleThreadExecutor(Thread.ofVirtual().name("music-prefetch-check-", 0).factory());
    }

    private static CompletableFuture<MusicDownload.Result> prepare(MusicPreparation<MusicDownload.Result> preparation,
            MusicPreparation.Key key, MusicPreparation.Operation<MusicDownload.Result> operation) {
        CompletableFuture<MusicDownload.Result> result = new CompletableFuture<>();
        preparation.prepare(key, operation, result::complete, result::completeExceptionally);
        return result;
    }

    private static MusicDownload.Result get(CompletableFuture<MusicDownload.Result> result) throws Exception {
        return result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static Throwable failure(CompletableFuture<MusicDownload.Result> result) throws Exception {
        try { get(result); throw new AssertionError("Expected a preparation failure"); }
        catch (ExecutionException failure) { return failure.getCause(); }
    }

    private static void drain(ExecutorService worker) throws Exception {
        // A serial executor barrier proves the speculative task and coordinator completion have both returned.
        worker.submit(() -> { }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void await(CountDownLatch latch, String message) throws InterruptedException {
        require(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), message);
    }

    private static List<Path> cacheFiles(Path cache) throws IOException {
        if (!Files.isDirectory(cache)) return List.of();
        try (var files = Files.list(cache)) { return files.filter(Files::isRegularFile).toList(); }
    }

    private static Path seed(Path cache, String filename, long modifiedMillis) throws IOException {
        Path path = cache.resolve(filename);
        Files.write(path, Fixture.audio("full", "standard"));
        Files.setLastModifiedTime(path, FileTime.fromMillis(modifiedMillis));
        return path;
    }

    private static void cleanTemporaryPlayback(Context context, MusicTrack track) throws IOException {
        require(context.library.downloaded(track) == null, "Playback prefetch silently registered a permanent download");
        try (var files = Files.list(context.libraryDirectory)) {
            require(files.findAny().isEmpty(), "Playback prefetch wrote a download or index into the user's music library");
        }
        require(cacheFiles(context.cache).stream().noneMatch(path -> path.getFileName().toString().endsWith(".part")),
            "Preparation leaked a temporary partial audio file");
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Context {
        private final Path libraryDirectory, cache;
        private final MusicLibraryStore library;
        private final MusicDownload download;

        private Context(Path root, Fixture fixture) throws IOException {
            libraryDirectory = root.resolve("music");
            cache = root.resolve("cache");
            library = new MusicLibraryStore(libraryDirectory);
            download = new MusicDownload(new NeteaseMusicApi(fixture.origin), library, libraryDirectory, cache);
        }

        private MusicDownload.Result playback(MusicTrack track, String quality, String cookie,
                MusicPreparation.Cancellation cancellation) throws MusicError, IOException {
            return download.playback(track, quality, cookie, () -> null, Set::of, _ -> { }, cancellation);
        }
    }

    private static final class Gate {
        private final CountDownLatch started;
        private final CountDownLatch release = new CountDownLatch(1);

        private Gate(int requests) { started = new CountDownLatch(requests); }
    }

    private static final class Fixture implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService httpWorker = Executors.newVirtualThreadPerTaskExecutor();
        private final Map<Long, Gate> gates = new ConcurrentHashMap<>();
        private final Map<Long, AtomicInteger> apiRequests = new ConcurrentHashMap<>(), mediaRequests = new ConcurrentHashMap<>();
        private final Map<Long, AtomicInteger> redirectRequests = new ConcurrentHashMap<>(), bodyWrites = new ConcurrentHashMap<>();
        private final Map<Long, String> userAgents = new ConcurrentHashMap<>();
        private final Set<Long> revoked = ConcurrentHashMap.newKeySet();
        private final Set<Long> blockedHeaders = ConcurrentHashMap.newKeySet(), redirects = ConcurrentHashMap.newKeySet();
        private final Set<Long> chunked = ConcurrentHashMap.newKeySet(), oversized = ConcurrentHashMap.newKeySet(),
            truncated = ConcurrentHashMap.newKeySet();
        private final URI origin;

        private Fixture() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            server.setExecutor(httpWorker);
            server.createContext("/", this::respond);
            server.start();
        }

        private Gate gate(long trackId, int requests) {
            Gate gate = new Gate(requests);
            gates.put(trackId, gate);
            return gate;
        }

        private int apiCalls(long id) { return apiRequests.getOrDefault(id, new AtomicInteger()).get(); }
        private int mediaCalls(long id) { return mediaRequests.getOrDefault(id, new AtomicInteger()).get(); }
        private int redirectCalls(long id) { return redirectRequests.getOrDefault(id, new AtomicInteger()).get(); }

        private void respond(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/song/url/v1")) {
                    Map<String, String> parameters = decode(exchange.getRequestMethod().equals("POST")
                        ? new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                        : exchange.getRequestURI().getRawQuery());
                    long id = Long.parseLong(parameters.get("id"));
                    apiRequests.computeIfAbsent(id, _ -> new AtomicInteger()).incrementAndGet();
                    String cookie = parameters.getOrDefault("cookie", "");
                    String access = cookie.equals("fixture-preview") ? "trial" : cookie.equals("fixture-unknown") ? "unknown" : "full";
                    String quality = parameters.getOrDefault("level", "standard");
                    String trial = access.equals("trial") ? "{\"start\":0,\"end\":30}" : access.equals("unknown") ? "{}" : "null";
                    String url = revoked.contains(id) ? "null" : "\"" + origin
                        + (redirects.contains(id) ? "/redirect/" : "/media/") + id + "/" + access + "/" + quality + "\"";
                    byte[] bytes = ("{\"code\":200,\"data\":[{\"id\":" + id + ",\"url\":" + url
                        + ",\"type\":\"" + (quality.equals("lossless") ? "flac" : "mp3")
                        + "\",\"fee\":1,\"freeTrialInfo\":" + trial + "}]}").getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } else if (path.startsWith("/redirect/")) {
                    String[] parts = path.split("/");
                    long id = Long.parseLong(parts[2]);
                    redirectRequests.computeIfAbsent(id, _ -> new AtomicInteger()).incrementAndGet();
                    exchange.getResponseHeaders().set("Location", "/media/" + id + "/" + parts[3] + "/" + parts[4]);
                    exchange.sendResponseHeaders(302, -1);
                } else if (path.startsWith("/media/")) {
                    String[] parts = path.split("/");
                    long id = Long.parseLong(parts[2]);
                    mediaRequests.computeIfAbsent(id, _ -> new AtomicInteger()).incrementAndGet();
                    String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
                    userAgents.put(id, userAgent == null ? "" : userAgent);
                    byte[] bytes = audio(parts[3], parts[4]);
                    Gate gate = gates.get(id);
                    boolean waitingForHeaders = blockedHeaders.contains(id);
                    if (gate != null && waitingForHeaders) {
                        gate.started.countDown();
                        if (!gate.release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) return;
                    }
                    if (oversized.contains(id)) {
                        exchange.sendResponseHeaders(200, 512L * 1024 * 1024 + 1);
                        // Flush only the headers. Closing now would race the client's size validation
                        // with a truncated-body error from the HTTP transport.
                        exchange.getResponseBody().flush();
                        if (gate != null) {
                            gate.started.countDown();
                            gate.release.await(TIMEOUT_SECONDS * 2L, TimeUnit.SECONDS);
                        }
                        return;
                    }
                    exchange.sendResponseHeaders(200, chunked.contains(id) ? 0 : bytes.length + (truncated.contains(id) ? 128 : 0));
                    if (gate != null && !waitingForHeaders) {
                        exchange.getResponseBody().write(bytes, 0, 128);
                        bodyWrites.computeIfAbsent(id, _ -> new AtomicInteger()).addAndGet(128);
                        exchange.getResponseBody().flush();
                        gate.started.countDown();
                        if (!gate.release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) return;
                        exchange.getResponseBody().write(bytes, 128, bytes.length - 128);
                        bodyWrites.get(id).addAndGet(bytes.length - 128);
                    } else {
                        exchange.getResponseBody().write(bytes);
                        bodyWrites.computeIfAbsent(id, _ -> new AtomicInteger()).addAndGet(bytes.length);
                    }
                } else exchange.sendResponseHeaders(404, -1);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally { exchange.close(); }
        }

        private static byte[] audio(String access, String quality) {
            byte[] bytes = new byte[64 * 1024];
            byte[] marker = ((quality.equals("lossless") ? "fLaC" : "ID3") + access + ":" + quality)
                .getBytes(StandardCharsets.UTF_8);
            System.arraycopy(marker, 0, bytes, 0, marker.length);
            Arrays.fill(bytes, marker.length, bytes.length, (byte) (access.equals("trial") ? 7 : access.equals("unknown") ? 9 : 11));
            return bytes;
        }

        private static Map<String, String> decode(String form) {
            Map<String, String> parameters = new HashMap<>();
            if (form == null || form.isBlank()) return parameters;
            for (String pair : form.split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) parameters.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
            return parameters;
        }

        @Override public void close() {
            for (Gate gate : gates.values()) gate.release.countDown();
            server.stop(0);
            httpWorker.shutdownNow();
        }
    }
}
