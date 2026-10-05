package cn.pupperclient.music;

import cn.pupperclient.management.music.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Real loopback HTTP and saved settings/session fixtures; no public API, SMS or live credentials. */
public final class MusicCloudLibraryChecks {
    private static int checks;
    private static final MusicAccount ACCOUNT = new MusicAccount("fixture-session-A", "41", "测试帐号", "");
    private MusicCloudLibraryChecks() { }
    public static void main(String[] arguments) throws Exception { run(); }
    public static void run() throws Exception {
        checks = 0;
        Path root = Files.createTempDirectory("pupper-cloud-checks-");
        try (var first = new Fixture(); var second = new Fixture()) {
            configurationChecks(root, first, second);
            credentialChecks(root, first, second);
            pendingLoginChecks(root, first, second);
            playlistChecks(root, first, second);
            metadataCancellation(first);
            collectionCancellation(first);
            System.out.println("Music cloud/API checks passed: " + checks + " assertions; endpoint trust/origin binding, "
                + "no credential redirects, blocked request cancellation, account playlist paging and expiry isolation.");
        } finally {
            try (var paths = Files.walk(root)) { for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
    private static void collectionCancellation(Fixture fixture) throws Exception {
        var netease = new NeteaseMusicProvider(new NeteaseMusicApi(fixture.origin));
        var audius = new AudiusMusicProvider(fixture.origin);
        for (MusicProvider provider : List.of(netease, audius)) for (var type : List.of(MusicSearchType.ARTISTS, MusicSearchType.PLAYLISTS)) {
            var collection = new MusicCollection(provider.id(), "42", type, "Fixture collection", "", "", 1243);
            String path = provider == netease ? type == MusicSearchType.ARTISTS ? "/artist/songs" : "/playlist/track/all"
                : type == MusicSearchType.ARTISTS ? "/users/42/tracks" : "/playlists/42/tracks";
            for (boolean body : List.of(false, true)) {
                Gate gate = fixture.block(path, body); var cancellation = new MusicPreparation.Cancellation();
                var outcome = new java.util.concurrent.CompletableFuture<Object>();
                try (var worker = new Workers()) {
                    worker.execute(() -> { try { outcome.complete(provider.collectionTracks(collection, 50, 0, ACCOUNT.cookie(), cancellation)); }
                        catch (Throwable failure) { outcome.completeExceptionally(failure); } });
                    try {
                        require(gate.entered.await(5, TimeUnit.SECONDS), "Collection paging did not reach its blocked HTTP fixture");
                        cancellation.cancel();
                        await(() -> worker.running.get() == 0, 3_000, "Cancelled collection paging kept its HTTP worker blocked");
                        require(outcome.isCompletedExceptionally(), "A cancelled collection page returned a usable result");
                        if (provider == audius) require(!fixture.last.containsKey("cookie"), "Collection paging forwarded NetEase credentials to Audius");
                    } finally { gate.release.countDown(); fixture.gate = null; }
                }
            }
            var cancellation = new MusicPreparation.Cancellation(); cancellation.cancel(); int before = fixture.requests.get();
            expect("music.error.network", () -> provider.collectionTracks(collection, 50, 0, ACCOUNT.cookie(), cancellation));
            require(fixture.requests.get() == before, "Already-cancelled collection paging contacted the API");
        }
    }
    private static void metadataCancellation(Fixture fixture) throws Exception {
        var netease = new NeteaseMusicProvider(new NeteaseMusicApi(fixture.origin));
        var audius = new AudiusMusicProvider(fixture.origin);
        for (MusicProvider provider : List.of(netease, audius)) {
            var track = new MusicTrack(provider.id().equals("netease") ? 42 : 0, "Fixture audio", "Artist", "", "", 10_000,
                provider.id(), provider.id().equals("netease") ? "42" : "fixture-track", true, true);
            for (boolean details : List.of(false, true)) for (boolean body : List.of(false, true)) {
                String path = provider.id().equals("netease") ? details ? "/song/detail" : "/song/url/v1" : "/tracks/fixture-track";
                Gate gate = fixture.block(path, body); var cancellation = new MusicPreparation.Cancellation();
                var outcome = new java.util.concurrent.CompletableFuture<Object>();
                try (var worker = new Workers()) {
                    worker.execute(() -> { try { outcome.complete(details ? provider.track(track.providerId(), cancellation)
                            : provider.audio(track, "standard", ACCOUNT.cookie(), false, cancellation)); }
                        catch (Throwable failure) { outcome.completeExceptionally(failure); } });
                    try {
                        require(gate.entered.await(5, TimeUnit.SECONDS), provider.id() + " URL metadata fixture did not reach blocked " + (body ? "body" : "headers"));
                        long started = System.nanoTime(); cancellation.cancel();
                        require(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 3_000, "Cancelling " + provider.id() + " metadata blocked");
                        await(() -> worker.running.get() == 0, 3_000, provider.id() + " cancelled metadata worker remained blocked");
                        require(outcome.isCompletedExceptionally(), provider.id() + " cancelled URL metadata returned a usable source");
                        if (provider.id().equals("audius")) require(!fixture.last.containsKey("cookie"), "Audius forwarded NetEase account credentials");
                    } finally { gate.release.countDown(); fixture.gate = null; }
                }
            }
            int before = fixture.requests.get(); var cancelled = new MusicPreparation.Cancellation(); cancelled.cancel();
            expect("music.error.network", () -> provider.audio(track, "standard", ACCOUNT.cookie(), false, cancelled));
            require(fixture.requests.get() == before, provider.id() + " already-cancelled metadata operation contacted the API");
            expect("music.error.network", () -> provider.track(track.providerId(), cancelled));
            require(fixture.requests.get() == before, provider.id() + " already-cancelled optional detail contacted the API");
            var source = provider.audio(track, "standard", ACCOUNT.cookie(), false, new MusicPreparation.Cancellation());
            require(source.extension().equals(".mp3") && source.uri().getPath().endsWith(provider.id().equals("netease") ? "/media.mp3" : "/stream"),
                provider.id() + " normal cancellable metadata path lost its audio endpoint/access refresh");
            require(provider.track(track.providerId(), new MusicPreparation.Cancellation()).sameSong(track),
                provider.id() + " normal cancellable detail lost provider-qualified identity");
        }
    }
    private static void configurationChecks(Path root, Fixture first, Fixture second) throws Exception {
        var publicApi = new NeteaseMusicApi(NeteaseMusicApi.DEFAULT_ORIGIN);
        expect("music.api.error.untrusted", () -> publicApi.sendCaptcha("13800000000"));
        expect("music.api.error.untrusted", publicApi::createLoginQr);
        expect("music.api.error.untrusted", () -> publicApi.audio(42, "standard", ACCOUNT.cookie()));
        for (String endpoint : List.of("http://example.com/", "https://user:secret@example.com/", "https://example.com/?cookie=secret",
                "https://example.com/#secret", "file:///tmp/api", "https://example.com:0/", "https://example.com:65536/",
                "http://localhost.example.com/", "https://example.com/%2fother", "https://example.com/%2e%2e/")) {
            try { MusicApiConfiguration.validate(URI.create(endpoint)); throw new AssertionError("Unsafe API endpoint accepted"); }
            catch (IllegalArgumentException expected) { checks++; require(!expected.getMessage().contains("secret"), "Configuration error echoed private URI text"); }
        }
        require(MusicApiConfiguration.validate(URI.create("HTTPS://EXAMPLE.COM:443/music")).toString().equals("https://example.com/music/"),
            "HTTPS API origin was not canonicalized");
        require(MusicApiConfiguration.validate(URI.create("http://[::1]:3000")).toString().equals("http://[::1]:3000/"), "IPv6 loopback API rejected");
        Path file = root.resolve("music-api.json"); var config = new MusicApiConfiguration(file);
        require(!config.snapshot().trustedAccounts(), "New settings trusted the public API without consent");
        config.configure(first.origin, true);
        String saved = Files.readString(file);
        require(saved.contains("trustedAccounts") && !saved.contains(ACCOUNT.cookie()) && !saved.contains("phone") && !saved.contains("captcha"),
            "Endpoint settings persisted account secrets");
        var restored = new MusicApiConfiguration(file);
        require(restored.snapshot().trustedAccounts() && restored.snapshot().endpoint().equals(first.origin), "Explicit endpoint trust did not survive restart");
        var store = new MusicAccountStore(root.resolve("bound-session.json"), config); store.save(ACCOUNT);
        require(store.snapshot().equals(ACCOUNT), "Bound account disappeared at its trusted endpoint");
        config.configure(second.origin, true);
        require(!store.snapshot().authenticated() && !new MusicAccountStore(root.resolve("bound-session.json"), config).snapshot().authenticated(),
            "Switching endpoint reused the old server's cookie after restart");
        config.configure(first.origin, false); require(!store.snapshot().authenticated(), "Revoked trust exposed a saved account");
        config.configure(first.origin, true); require(store.snapshot().equals(ACCOUNT), "Re-selecting the same explicitly trusted endpoint lost its bound account");
        Path legacy = root.resolve("legacy-session.json");
        Files.writeString(legacy, "{\"cookie\":\"fixture-legacy\",\"userId\":\"41\",\"savedTime\":" + System.currentTimeMillis() + "}");
        require(!new MusicAccountStore(legacy, config).snapshot().authenticated(), "Unbound legacy cookie migrated to a newly configured server");
        config.configure(NeteaseMusicApi.DEFAULT_ORIGIN, false);
        require(!new MusicAccountStore(legacy, config).snapshot().authenticated(), "Anonymous default loaded a legacy public-server cookie");
    }
    private static void credentialChecks(Path root, Fixture first, Fixture second) throws Exception {
        var api = new NeteaseMusicApi(first.origin);
        api.likes(ACCOUNT.userId(), ACCOUNT.cookie());
        require(first.last.get("cookie").equals(ACCOUNT.cookie()), "Trusted origin fixture did not receive its own cookie");
        int untouched = second.requests.get(); api.configure(second.origin, true);
        expect("music.api.error.changed", () -> api.likes(ACCOUNT.userId(), ACCOUNT.cookie()));
        require(second.requests.get() == untouched, "Queued old cookie was transmitted to a new endpoint");
        long changedRevision = api.configuration().snapshot().revision(); api.configure(first.origin, true);
        int metadataBefore = first.requests.get();
        expect("music.api.error.changed", () -> api.details(List.of(42L), changedRevision));
        require(first.requests.get() == metadataBefore, "Old account metadata IDs were transmitted after an endpoint revision change");
        var qr = api.createLoginQr(); api.configure(second.origin, true);
        expect("music.api.error.changed", () -> api.checkLoginQr(qr.key()));
        require(second.requests.get() == untouched, "Old QR key was transmitted to a different server");
        api.configure(first.origin, true); first.redirect = second.origin.resolve("capture");
        try {
            for (int status : List.of(302, 307)) {
                first.redirectStatus = status;
                expect("music.error.network", () -> api.sendCaptcha("13800000000"));
                expect("music.error.network", () -> api.like(42, true, ACCOUNT.cookie()));
            }
        } finally { first.redirect = null; }
        require(second.requests.get() == untouched, "Authentication redirect replayed a phone/cookie to another origin");
        var config = new MusicApiConfiguration(root.resolve("anonymous-custom-api.json")); config.configure(first.origin, false);
        var anonymous = new NeteaseMusicApi(config);
        require(anonymous.search("fixture", 10, 0).tracks().size() == 1, "Untrusted endpoint disabled harmless anonymous search");
        expect("music.api.error.untrusted", () -> anonymous.sendCaptcha("13800000000"));
        require(!first.last.containsKey("cookie") && !first.last.containsKey("phone"), "Anonymous search carried saved private data");
    }
    private static void pendingLoginChecks(Path root, Fixture first, Fixture second) throws Exception {
        var api = new NeteaseMusicApi(first.origin);
        var store = new MusicAccountStore(root.resolve("pending-login.json"), api.configuration()); store.save(ACCOUNT);
        var callbacks = new ConcurrentLinkedQueue<Runnable>(); var completed = new AtomicInteger();
        try (var workers = new Workers()) {
            var login = new MusicLoginService(api, store, workers, callbacks::add, (task, delay) -> { });
            Gate gate = first.block("/login/cellphone", false);
            try {
                login.phoneLogin("13800000000", "123456", _ -> completed.incrementAndGet(), _ -> completed.incrementAndGet());
                require(gate.entered.await(5, TimeUnit.SECONDS), "Delayed auth fixture never received the private POST");
                long started = System.nanoTime(); api.configure(second.origin, true);
                require(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 3_000, "Endpoint change blocked while cancelling pending auth headers");
                await(() -> workers.running.get() == 0, 3_000, "Old authentication request did not stop after endpoint change");
                drain(callbacks);
                require(completed.get() == 0 && !login.account().authenticated(), "Old auth reply committed/emitted after endpoint change");
                require(!Files.readString(root.resolve("pending-login.json")).contains("fixture-login"), "Cancelled login response was saved");
            } finally { gate.release.countDown(); first.gate = null; login.close(); }
        }
    }
    private static void playlistChecks(Path root, Fixture first, Fixture second) throws Exception {
        var api = new NeteaseMusicApi(first.origin);
        var store = new MusicAccountStore(root.resolve("cloud-session.json"), api.configuration()); store.save(ACCOUNT);
        var callbacks = new ConcurrentLinkedQueue<Runnable>(); var pages = new ArrayList<MusicCloudLibrary.Page>(); var errors = new ArrayList<String>();
        try (var workers = new Workers()) {
            var login = new MusicLoginService(api, store, workers, callbacks::add, (task, delay) -> { });
            try (var cloud = new MusicCloudLibrary(api, login, workers, callbacks::add)) {
                cloud.playlists(3, 0, pages::add, error -> errors.add(error.key())); complete(workers, callbacks);
                var page = pages.getLast();
                require(page.created().size() == 1 && page.created().getFirst().id().equals("101") && page.subscribed().size() == 1
                    && page.subscribed().getFirst().id().equals("102"), "Cloud playlists did not split own/followed creators");
                require(page.offset() == 0 && page.nextOffset() == 3 && page.more(), "Malformed entries corrupted raw playlist pagination");
                require(first.last.get("uid").equals("41") && first.last.get("cookie").equals(ACCOUNT.cookie()), "Cloud playlist request used another account");
                cloud.playlists(3, page.nextOffset(), pages::add, error -> errors.add(error.key())); complete(workers, callbacks);
                require(pages.getLast().created().getFirst().id().equals("103") && pages.getLast().nextOffset() == 4 && !pages.getLast().more(),
                    "Second cloud page lost its offset/terminal state");

                int before = pages.size(); var queued = cloud.playlists(3, 0, pages::add, error -> errors.add(error.key()));
                await(() -> !callbacks.isEmpty() && workers.running.get() == 0, 5_000, "Completed page did not queue its callback");
                queued.close(); drain(callbacks);
                require(pages.size() == before && errors.isEmpty(), "Leaving cloud playlists delivered a queued stale page");

                Gate gate = first.block("/user/playlist", true);
                try {
                    var blocked = cloud.playlists(3, 0, pages::add, error -> errors.add(error.key()));
                    require(gate.entered.await(5, TimeUnit.SECONDS), "Blocked playlist body fixture did not start");
                    long started = System.nanoTime(); blocked.close();
                    require(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 3_000, "Closing cloud page blocked on the response body");
                    await(() -> workers.running.get() == 0, 3_000, "Dismissed cloud body remained in flight"); drain(callbacks);
                    require(pages.size() == before && errors.isEmpty(), "Cancelled cloud request emitted a page/error");
                } finally { gate.release.countDown(); first.gate = null; }

                gate = first.block("/user/playlist", true);
                try {
                    cloud.playlists(3, 0, pages::add, error -> errors.add(error.key()));
                    require(gate.entered.await(5, TimeUnit.SECONDS), "Old account cloud request did not reach its response body");
                    login.phoneLogin("13800000000", "123456", _ -> { }, error -> errors.add(error.key()));
                    complete(workers, callbacks);
                    require(pages.size() == before && login.account().userId().equals("777"), "Old account page survived replacement login");
                } finally { gate.release.countDown(); first.gate = null; }
                store.save(ACCOUNT);
                // Setting a session directly is fixture setup; service callbacks are still scoped to the exact snapshot.
                gate = first.block("/user/playlist", true);
                try {
                    cloud.playlists(3, 0, pages::add, error -> errors.add(error.key()));
                    require(gate.entered.await(5, TimeUnit.SECONDS), "Old endpoint cloud request did not start");
                    api.configure(second.origin, true); await(() -> workers.running.get() == 0, 3_000, "Old cloud request survived endpoint change"); drain(callbacks);
                    require(pages.size() == before && !login.account().authenticated(), "Cloud page crossed an endpoint/account boundary");
                } finally { gate.release.countDown(); first.gate = null; }
                api.configure(first.origin, true); store.save(ACCOUNT); first.expired = true;
                try {
                    cloud.playlists(3, 0, pages::add, error -> errors.add(error.key())); complete(workers, callbacks);
                    require(errors.equals(List.of("music.error.login")) && !login.account().authenticated(), "Rejected cloud session was not cleared/reported once");
                    require(!new MusicAccountStore(root.resolve("cloud-session.json"), api.configuration()).snapshot().authenticated(), "Expired cloud session returned on restart");
                } finally { first.expired = false; }
            } finally { login.close(); }
        }
        var queuedWorkers = new ConcurrentLinkedQueue<Runnable>(); var queuedCallbacks = new ConcurrentLinkedQueue<Runnable>();
        store.save(ACCOUNT); var login = new MusicLoginService(api, store, queuedWorkers::add, queuedCallbacks::add, (task, delay) -> { });
        try (var cloud = new MusicCloudLibrary(api, login, queuedWorkers::add, queuedCallbacks::add)) {
            int before = first.requests.get(); var pending = cloud.playlists(3, 0, _ -> { }, error -> { throw new AssertionError(error.key()); });
            pending.close(); drain(queuedWorkers); drain(queuedCallbacks);
            require(first.requests.get() == before, "Cancelled queued cloud request still contacted the API");
            var rejectOnce = new AtomicInteger();
            Executor rejecting = task -> { if (rejectOnce.getAndIncrement() == 0) throw new java.util.concurrent.RejectedExecutionException(); task.run(); };
            try (var retryable = new MusicCloudLibrary(api, login, rejecting, Runnable::run)) {
                var failures = new AtomicInteger(); var success = new AtomicInteger();
                retryable.playlists(3, 0, _ -> success.incrementAndGet(), _ -> failures.incrementAndGet());
                retryable.playlists(3, 0, _ -> success.incrementAndGet(), _ -> failures.incrementAndGet());
                require(failures.get() == 1 && success.get() == 1, "Rejected cloud worker stranded subsequent pages");
            }
        } finally { login.close(); }
    }
    private static void complete(Workers workers, ConcurrentLinkedQueue<Runnable> callbacks) throws Exception {
        await(() -> workers.running.get() == 0 && !callbacks.isEmpty(), 5_000, "Music cloud/auth task did not complete"); drain(callbacks);
    }
    private static void drain(ConcurrentLinkedQueue<Runnable> queue) { for (Runnable task; (task = queue.poll()) != null;) task.run(); }
    private static void await(BooleanSupplier condition, long millis, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        require(condition.getAsBoolean(), message);
    }
    @FunctionalInterface private interface Operation { void run() throws Exception; }
    private static void expect(String key, Operation operation) throws Exception {
        try { operation.run(); throw new AssertionError("Expected " + key); }
        catch (MusicError error) { require(error.key().equals(key), "Unexpected API failure: " + error.key()); }
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static final class Workers implements Executor, AutoCloseable {
        final java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        final AtomicInteger running = new AtomicInteger();
        @Override public void execute(Runnable task) { running.incrementAndGet(); executor.execute(() -> { try { task.run(); } finally { running.decrementAndGet(); } }); }
        @Override public void close() { executor.close(); }
    }
    private static final class Gate {
        final String path; final boolean body;
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Gate(String path, boolean body) { this.path = path; this.body = body; }
    }
    private static final class Fixture implements AutoCloseable {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        final URI origin;
        final AtomicInteger requests = new AtomicInteger();
        volatile Map<String, String> last = Map.of();
        volatile Gate gate; volatile URI redirect; volatile int redirectStatus = 302; volatile boolean expired;
        Fixture() throws IOException { server.setExecutor(executor); server.createContext("/", this::respond); server.start(); origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"); }
        Gate block(String path, boolean body) { return gate = new Gate(path, body); }
        void respond(HttpExchange exchange) throws IOException {
            try {
                requests.incrementAndGet(); String path = exchange.getRequestURI().getPath();
                boolean post = exchange.getRequestMethod().equals("POST");
                last = decode(post ? new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8) : exchange.getRequestURI().getRawQuery());
                if (last.containsKey("cookie") || path.startsWith("/login") || path.equals("/captcha/sent")) {
                    require(post && exchange.getRequestURI().getRawQuery() == null && exchange.getRequestHeaders().getFirst("Cookie") == null,
                        "Private API data appeared in a GET URL or transport Cookie header");
                }
                URI target = redirect;
                if (target != null) { exchange.getResponseHeaders().set("Location", target.toString()); exchange.sendResponseHeaders(redirectStatus, -1); return; }
                if (expired && path.equals("/user/playlist")) { exchange.sendResponseHeaders(401, -1); return; }
                String text = switch (path) {
                    case "/likelist" -> "{\"code\":200,\"ids\":[42]}";
                    case "/like", "/captcha/sent", "/logout" -> "{\"code\":200}";
                    case "/login/cellphone" -> "{\"code\":200,\"cookie\":\"fixture-login\",\"account\":{\"id\":777},\"profile\":{\"userId\":777,\"nickname\":\"New account\"}}";
                    case "/login/qr/key" -> "{\"code\":200,\"data\":{\"code\":200,\"unikey\":\"fixture-key-origin\"}}";
                    case "/login/qr/create" -> "{\"code\":200,\"data\":{\"qrurl\":\"https://music.163.com/login?codekey=fixture\",\"qrimg\":\"data:image/png;base64,fixture\"}}";
                    case "/cloudsearch", "/search" -> "{\"code\":200,\"result\":{\"songCount\":1,\"songs\":[{\"id\":42,\"name\":\"Anonymous song\"}]}}";
                    case "/song/url/v1" -> "{\"code\":200,\"data\":[{\"id\":42,\"type\":\"mp3\",\"fee\":0,\"url\":\"" + origin.resolve("media.mp3") + "\"}]}";
                    case "/song/detail" -> "{\"code\":200,\"songs\":[{\"id\":42,\"name\":\"NetEase fixture\"}]}";
                    case "/tracks/fixture-track" -> "{\"data\":{\"id\":\"fixture-track\",\"title\":\"Audius fixture\",\"duration\":10,\"is_streamable\":true,\"is_downloadable\":true,\"user\":{\"name\":\"Artist\"}}}";
                    case "/user/playlist" -> last.getOrDefault("offset", "0").equals("0")
                        ? "{\"code\":200,\"more\":true,\"playlist\":[" + playlist(101, "Own list", 41) + "," + playlist(102, "Followed list", 99) + ",{\"name\":\"Malformed entry\"}]}"
                        : "{\"code\":200,\"more\":false,\"playlist\":[" + playlist(103, "Second own list", 41) + "]}";
                    default -> "{\"code\":200}";
                };
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8); Gate pending = gate;
                if (pending != null && pending.path.equals(path) && !pending.body) hold(pending);
                exchange.sendResponseHeaders(200, bytes.length);
                try (var output = exchange.getResponseBody()) {
                    if (pending != null && pending.path.equals(path) && pending.body) { output.write(bytes, 0, 1); output.flush(); hold(pending); output.write(bytes, 1, bytes.length - 1); }
                    else output.write(bytes);
                }
            } catch (IOException cancelled) { /* A client cancellation is expected in these bounded local fixtures. */ }
            finally { exchange.close(); }
        }
        private static void hold(Gate gate) throws IOException {
            gate.entered.countDown();
            try { if (!gate.release.await(10, TimeUnit.SECONDS)) throw new IOException("Fixture gate timeout"); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
        }
        @Override public void close() { if (gate != null) gate.release.countDown(); server.stop(0); executor.close(); }
        private static String playlist(int id, String name, int owner) { return "{\"id\":" + id + ",\"name\":\"" + name + "\",\"trackCount\":5,\"creator\":{\"userId\":" + owner + ",\"nickname\":\"Creator\"}}"; }
        private static Map<String, String> decode(String form) {
            var result = new java.util.HashMap<String, String>();
            if (form != null && !form.isEmpty()) for (String pair : form.split("&")) { String[] parts = pair.split("=", 2);
                result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts.length > 1 ? parts[1] : "", StandardCharsets.UTF_8)); }
            return result;
        }
    }
}
