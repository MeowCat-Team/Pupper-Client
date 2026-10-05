package cn.pupperclient.music;

import cn.pupperclient.gui.modmenu.component.MusicDownloadsUi;
import cn.pupperclient.gui.modmenu.component.MusicPlayerLayout;
import cn.pupperclient.management.music.*;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real HTTP proves queue limits, cancellation and complete-file publication, not just task labels. */
public final class MusicDownloadTasksChecks {
    private static int checks;
    public static void run() throws Exception {
        checks = 0; Path root = Files.createTempDirectory("pupper-download-tasks-");
        try (Fixture fixture = new Fixture()) {
            boundedBatch(root.resolve("batch"), fixture);
            headerCancellation(root.resolve("headers"), fixture);
            failedRetry(root.resolve("retry"), fixture);
            previewUpgrade(root.resolve("preview"), fixture);
            cancellationRetryRace(); callbackLifecycle(); geometry();
            System.out.println("Music download task checks passed: " + checks + " assertions; bounded batch, active deduplication, queued/header/body "
                + "cancellation, retry identity, permission/preview upgrade, live playing-file protection and callback lifecycle.");
        } finally {
            try (var files = Files.walk(root)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
    }
    private static void boundedBatch(Path root, Fixture fixture) throws Exception {
        var context = new Context(root, fixture); var first = fixture.gate(1, false); var second = fixture.gate(2, false);
        AtomicInteger joinedResults = new AtomicInteger();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor();
                var tasks = context.tasks(worker)) {
            var ids = tasks.submitBatch(List.of(track(1), track(1), track(2), track(3), track(4)), "standard", "fixture-guest");
            await(first.started, "First body never started"); await(second.started, "Second body never started");
            require(ids.size() == 4 && tasks.list().size() == 4, "A batch downloaded its repeated song twice");
            require(tasks.list().stream().filter(task -> task.state() == MusicDownloadTasks.State.RUNNING).count() == 2
                && tasks.list().stream().filter(task -> task.state() == MusicDownloadTasks.State.QUEUED).count() == 2,
                "Default download concurrency failed to keep exactly two transfers active");
            long joined = tasks.submit(track(1), "standard", "fixture-guest");
            require(joined == ids.getFirst() && fixture.calls(1) == 1, "An active duplicate started another media request");
            tasks.submit(track(2), "standard", "fixture-guest", _ -> joinedResults.incrementAndGet(), _ -> { });
            tasks.submit(track(2), "standard", "fixture-guest", _ -> joinedResults.incrementAndGet(), _ -> { });
            var immutable = tasks.list();
            require(tasks.cancel(ids.getLast()) && !tasks.cancel(ids.getLast()) && fixture.calls(4) == 0,
                "Cancelling a queued task fetched audio or ran twice");
            try (var control = Executors.newVirtualThreadPerTaskExecutor()) {
                require(control.submit(() -> tasks.cancel(ids.getFirst())).get(2, TimeUnit.SECONDS), "Body cancellation was rejected");
            }
            await(context.stopped(1), "Body cancellation did not unblock the worker"); first.release.countDown(); second.release.countDown();
            eventually(() -> task(tasks, ids.get(1)).state() == MusicDownloadTasks.State.COMPLETED
                && task(tasks, ids.get(2)).state() == MusicDownloadTasks.State.COMPLETED
                && context.completed.get() == 2 && joinedResults.get() == 2, "Queued download or joined callers did not finish after cancellation");
            require(context.maximum.get() <= 2 && context.completed.get() == 2, "Cancelled workers exceeded concurrency or triggered success");
            require(immutable.getFirst().state() == MusicDownloadTasks.State.RUNNING, "Task snapshots mutated after queue edits");
            require(task(tasks, ids.getFirst()).state() == MusicDownloadTasks.State.CANCELLED && !context.libraryFiles().stream()
                .anyMatch(path -> path.getFileName().toString().contains("[1]")), "Body cancellation published incomplete library audio");
            require(noPartials(context.directory), "Cancelled body left a partial in the music library");
            require(!tasks.list().toString().contains("fixture-guest"), "Credential snapshot appeared in public task state");
        } finally { first.release.countDown(); second.release.countDown(); }
    }
    private static void headerCancellation(Path root, Fixture fixture) throws Exception {
        var context = new Context(root, fixture); var gate = fixture.gate(10, true);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor(); var tasks = context.tasks(worker)) {
            long id = tasks.submit(track(10), "standard", "fixture-guest"); await(gate.started, "Slow headers did not start");
            try (var control = Executors.newVirtualThreadPerTaskExecutor()) {
                require(control.submit(() -> tasks.cancel(id)).get(2, TimeUnit.SECONDS), "Header cancellation failed");
            }
            await(context.stopped(10), "Cancelling headers did not release a blocked HTTP wait");
            require(context.libraryFiles().isEmpty() && noPartials(context.directory), "Header cancellation published audio or leaked a partial");
            require(context.completed.get() == 0 && task(tasks, id).state() == MusicDownloadTasks.State.CANCELLED, "Cancelled headers reported success");
        } finally { gate.release.countDown(); }
    }
    private static void failedRetry(Path root, Fixture fixture) throws Exception {
        var context = new Context(root, fixture); fixture.failures.put(20L, new AtomicInteger(1));
        AtomicInteger joinedResults = new AtomicInteger();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor(); var tasks = context.tasks(worker)) {
            long id = tasks.submit(track(20), "standard", "fixture-guest", _ -> joinedResults.incrementAndGet(), _ -> joinedResults.addAndGet(10));
            eventually(() -> task(tasks, id).state() == MusicDownloadTasks.State.FAILED && joinedResults.get() == 10, "Failed HTTP request was not retained");
            require(task(tasks, id).errorKey().equals("music.error.network") && context.libraryFiles().isEmpty() && noPartials(context.directory),
                "Failed transfer lost its translated error or published audio");
            require(tasks.clearFinished() == 0 && tasks.list().size() == 1, "Clearing completed history silently removed a retryable failure");
            require(tasks.retry(id), "Failed download cannot be retried");
            eventually(() -> task(tasks, id).state() == MusicDownloadTasks.State.COMPLETED && context.completed.get() == 1, "Retry did not finish");
            require(task(tasks, id).attempts() == 2 && task(tasks, id).progress() == 100 && fixture.calls(20) == 2,
                "Retry changed task identity, restarted twice or skipped complete progress");
            require(!tasks.retry(id) && context.completed.get() == 1 && joinedResults.get() == 10,
                "Successful retry replayed an old failed subscriber or allowed pointless automatic retry");
            long denied = tasks.submit(new MusicTrack(21, "Restricted", "", "", "", 120000, "netease", "21", true, false), "standard", "fixture-guest");
            eventually(() -> task(tasks, denied).state() == MusicDownloadTasks.State.FAILED, "Restricted download did not fail");
            require(task(tasks, denied).errorKey().equals("music.error.downloadrestricted") && fixture.calls(21) == 0,
                "Task centre bypassed the provider's download permission");
        }
    }
    private static void previewUpgrade(Path root, Fixture fixture) throws Exception {
        var context = new Context(root, fixture);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor(); var tasks = context.tasks(worker)) {
            long guest = tasks.submit(track(30), "standard", "fixture-guest");
            eventually(() -> task(tasks, guest).state() == MusicDownloadTasks.State.COMPLETED, "Guest preview download did not finish");
            var preview = task(tasks, guest);
            require(preview.track().previewMillis() == 30000 && preview.track().fee() == 1
                && context.library.metadata(preview.audio().getFileName().toString()).preview(), "VIP preview lost its thirty-second label or persisted access metadata");
            var gate = fixture.gate(30, false);
            long vip = tasks.submit(track(30), "standard", "fixture-vip");
            await(gate.started, "VIP upgrade did not reach its slow body");
            // Playback can begin while an upgrade is still downloading; publication consults the live path.
            context.playing.set(preview.audio()); gate.release.countDown();
            eventually(() -> task(tasks, vip).state() == MusicDownloadTasks.State.FAILED, "Playing preview replacement did not fail");
            require(vip != guest && task(tasks, vip).errorKey().equals("music.error.previewplaying")
                && Arrays.equals(Files.readAllBytes(preview.audio()), Fixture.audio(true)), "VIP replacement overwrote the actively playing preview");
            context.playing.set(null); require(tasks.retry(vip, "fixture-vip"), "Stopped preview cannot be upgraded by retry");
            eventually(() -> task(tasks, vip).state() == MusicDownloadTasks.State.COMPLETED, "Authorized VIP retry did not upgrade the preview");
            require(!task(tasks, vip).track().preview() && Arrays.equals(Files.readAllBytes(task(tasks, vip).audio()), Fixture.audio(false))
                && !context.library.metadata(task(tasks, vip).audio().getFileName().toString()).preview(),
                "VIP retry mislabeled thirty seconds as a full song or retained stale sidecar access");
            require(fixture.downloadPermissions.get() > 0, "Permanent audio acquisition used the playback-only permission route");
        }
    }
    private static void cancellationRetryRace() throws Exception {
        CountDownLatch started = new CountDownLatch(1), cancelled = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger(), completed = new AtomicInteger();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor(); var tasks = new MusicDownloadTasks(worker, Runnable::run,
            (track, quality, credentials, progress, token) -> {
                if (attempts.incrementAndGet() == 1) try (var hook = token.onCancel(cancelled::countDown)) {
                    started.countDown(); await(release, "Cancelled worker was not released"); token.check();
                }
                return new MusicDownload.Result(Path.of("fixture.mp3"), track);
            }, _ -> completed.incrementAndGet(), 1)) {
            long id = tasks.submit(track(50), "standard", "fixture"); await(started, "Retry race did not start");
            require(tasks.cancel(id), "Retry race could not cancel its first generation"); await(cancelled, "Cancellation hook did not run");
            require(tasks.retry(id), "Cancelled in-flight generation could not queue a retry");
            require(task(tasks, id).state() == MusicDownloadTasks.State.QUEUED && attempts.get() == 1,
                "Retry exceeded the limit before the old worker actually stopped");
            release.countDown(); eventually(() -> task(tasks, id).state() == MusicDownloadTasks.State.COMPLETED && completed.get() == 1,
                "An old cancelled generation overwrote the retry's successful state");
            require(attempts.get() == 2 && task(tasks, id).attempts() == 2, "Cancellation/retry scheduled an unexpected generation");
        } finally { release.countDown(); }
    }
    private static void callbackLifecycle() {
        var callbacks = new ArrayList<Runnable>(); var order = new ArrayList<String>();
        try (var tasks = new MusicDownloadTasks(Runnable::run, callbacks::add,
                (track, quality, credentials, progress, token) -> new MusicDownload.Result(Path.of("fixture.mp3"), track), _ -> order.add("library"))) {
            tasks.submit(track(60), "standard", "fixture", _ -> order.add("subscriber"), _ -> { });
            require(order.isEmpty() && callbacks.size() == 1, "Completion ignored the main-thread dispatcher");
            callbacks.removeFirst().run(); require(order.equals(List.of("library", "subscriber")), "Subscriber ran before the library completion hook");
            tasks.submit(track(61), "standard", "fixture"); tasks.close(); callbacks.removeFirst().run();
            require(order.equals(List.of("library", "subscriber")), "A stale main-thread callback ran after the download centre closed");
        }
    }
    private static void geometry() {
        var box = new MusicPlayerLayout.Box(224, 100, 1112, 600);
        for (var action : MusicDownloadsUi.Action.values()) {
            var target = MusicDownloadsUi.action(box, action);
            require(target.width() == 48 && target.height() == 48 && target.x() + target.width() <= box.x() + box.width()
                && target.contains(target.x() + 24, target.y() + 24), "Download toolbar lost its 48-unit click target");
        }
        var body = MusicDownloadsUi.body(box); var row = MusicDownloadsUi.rowAction(box, body.y());
        require(row.y() + row.height() < body.y() + MusicDownloadsUi.ROW_HEIGHT
            && !row.contains(row.x() + row.width(), row.y()), "Download row target overlaps the next row or shares its border");
    }
    private static MusicTrack track(long id) { return new MusicTrack(id, "Song " + id, "Artist", "", "", 120000).withAccess(id == 30 ? 1 : 0, 0); }
    private static MusicDownloadTasks.Task task(MusicDownloadTasks tasks, long id) { return tasks.list().stream().filter(task -> task.id() == id).findFirst().orElseThrow(); }
    private static void await(CountDownLatch latch, String message) throws InterruptedException { require(latch.await(5, TimeUnit.SECONDS), message); }
    private static void eventually(BooleanSupplier ready, String message) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!ready.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        require(ready.getAsBoolean(), message);
    }
    private static boolean noPartials(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return true;
        try (var files = Files.list(directory)) { return files.noneMatch(path -> path.getFileName().toString().endsWith(".part") || path.getFileName().toString().endsWith(".tmp")); }
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static final class Context {
        final Path directory, cache;
        final MusicLibraryStore library;
        final MusicDownload download;
        final AtomicReference<Path> playing = new AtomicReference<>();
        final AtomicInteger active = new AtomicInteger(), maximum = new AtomicInteger(), completed = new AtomicInteger();
        final Map<Long, CountDownLatch> stopped = new ConcurrentHashMap<>();
        Context(Path root, Fixture fixture) throws IOException { directory = root.resolve("music"); cache = root.resolve("cache"); library = new MusicLibraryStore(directory); download = new MusicDownload(fixture, library, directory, cache); }
        CountDownLatch stopped(long id) { return stopped.computeIfAbsent(id, _ -> new CountDownLatch(1)); }
        MusicDownloadTasks tasks(java.util.concurrent.Executor worker) {
            return new MusicDownloadTasks(worker, Runnable::run, (track, quality, credentials, progress, token) -> {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                try { return download.download(track, quality, credentials, playing::get, progress, token); }
                finally { active.decrementAndGet(); stopped(track.id()).countDown(); }
            }, _ -> completed.incrementAndGet());
        }
        List<Path> libraryFiles() throws IOException {
            if (!Files.isDirectory(directory)) return List.of();
            try (var files = Files.list(directory)) { return files.filter(path -> path.getFileName().toString().endsWith(".mp3")).toList(); }
        }
    }
    private static final class Gate { final boolean headers; final CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1); Gate(boolean headers) { this.headers = headers; } }
    private static final class Fixture implements MusicProvider, AutoCloseable {
        final HttpServer server;
        final java.util.concurrent.ExecutorService handlers = Executors.newVirtualThreadPerTaskExecutor();
        final Map<Long, Gate> gates = new ConcurrentHashMap<>();
        final Map<Long, AtomicInteger> media = new ConcurrentHashMap<>(), failures = new ConcurrentHashMap<>();
        final AtomicInteger downloadPermissions = new AtomicInteger();
        Fixture() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); server.setExecutor(handlers);
            server.createContext("/audio/", exchange -> {
                long id = Long.parseLong(exchange.getRequestURI().getPath().substring("/audio/".length()));
                media.computeIfAbsent(id, _ -> new AtomicInteger()).incrementAndGet(); Gate gate = gates.get(id);
                try {
                    if (gate != null && gate.headers) { gate.started.countDown(); gate.release.await(10, TimeUnit.SECONDS); }
                    AtomicInteger failure = failures.get(id);
                    if (failure != null && failure.getAndUpdate(value -> Math.max(0, value - 1)) > 0) { exchange.sendResponseHeaders(500, -1); return; }
                    byte[] audio = audio("preview=true".equals(exchange.getRequestURI().getQuery())); exchange.sendResponseHeaders(200, audio.length);
                    try (var stream = exchange.getResponseBody()) {
                        stream.write(audio, 0, 8); stream.flush();
                        if (gate != null && !gate.headers) { gate.started.countDown(); gate.release.await(10, TimeUnit.SECONDS); }
                        stream.write(audio, 8, audio.length - 8);
                    }
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                catch (IOException disconnected) { /* Cancellation closes the client while this gated server still writes. */ }
                finally { exchange.close(); }
            }); server.start();
        }
        Gate gate(long id, boolean headers) { var gate = new Gate(headers); gates.put(id, gate); return gate; }
        int calls(long id) { return media.getOrDefault(id, new AtomicInteger()).get(); }
        static byte[] audio(boolean preview) { byte[] bytes = new byte[preview ? 512 : 1024]; Arrays.fill(bytes, (byte) (preview ? 'P' : 'F')); bytes[0] = 'I'; bytes[1] = 'D'; bytes[2] = '3'; return bytes; }
        @Override public String id() { return "netease"; }
        @Override public List<String> qualities() { return List.of("standard"); }
        @Override public SearchResult search(String query, int limit, int offset) { return new SearchResult(List.of(), 0, offset); }
        @Override public MusicTrack track(String id) { return MusicDownloadTasksChecks.track(Long.parseLong(id)); }
        @Override public AudioSource audio(MusicTrack track, String quality, String credentials, boolean download) {
            require(download, "Permanent download called the playback-only API"); downloadPermissions.incrementAndGet();
            boolean preview = track.fee() == 1 && !credentials.equals("fixture-vip");
            return new AudioSource(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/audio/" + track.id() + "?preview=" + preview), ".mp3", track.fee(), preview ? 30000 : 0);
        }
        @Override public void close() { gates.values().forEach(gate -> gate.release.countDown()); server.stop(0); handlers.shutdownNow(); }
    }
}
