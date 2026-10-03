package cn.pupperclient.music;

import cn.pupperclient.management.music.*;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.language.Language;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Real local HTTP, persistence and deterministic session races; never sends SMS or uses a live account. */
final class MusicAccountChecks {
    private static final AtomicInteger checks = new AtomicInteger();
    private static final MusicAccount OLD = new MusicAccount("fixture-old", "101", "旧账号", "13800000000");

    static void run() throws Exception {
        Path root = Files.createTempDirectory("pupper-account-checks-");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        var fixture = new Fixture();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            require(exchange.getRequestMethod().equals("POST") && exchange.getRequestURI().getRawQuery() == null,
                "Login secrets appeared in a GET/query request");
            var params = decode(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            require(params.containsKey("timestamp") && exchange.getRequestHeaders().getFirst("Cookie") == null,
                "Auth request lost its timestamp or used a transport cookie");
            fixture.requests.incrementAndGet(); fixture.last = params;
            if (path.equals(fixture.failure)) { exchange.sendResponseHeaders(500, -1); exchange.close(); return; }
            boolean anonymous = fixture.anonymous;
            if (path.equals("/login/status") && fixture.blockStatus && !anonymous) {
                fixture.statusEntered.countDown();
                try { if (!fixture.statusRelease.await(10, TimeUnit.SECONDS)) throw new IOException("Fixture timeout"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            }
            if (path.equals("/login/cellphone") && params.get("phone").equals("13800000001") && fixture.block) {
                fixture.entered.countDown();
                try { if (!fixture.release.await(10, TimeUnit.SECONDS)) throw new IOException("Fixture timeout"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            }
            String response = switch (path) {
                case "/captcha/sent", "/logout" -> "{\"code\":200}";
                case "/login/cellphone" -> fixture.reject ? "{\"code\":502,\"message\":\"private response\"}" :
                    "{\"code\":200,\"cookie\":\"fixture-phone\"" + (fixture.fallback ? "" : profile(201)) + "}";
                case "/user/account" -> "{\"code\":200" + profile(303) + "}";
                case "/login/status" -> anonymous ? "{\"data\":{\"code\":200,\"account\":null,\"profile\":null}}" :
                    "{\"data\":{\"code\":200" + profile(101) + "}}";
                case "/login/refresh" -> "{\"code\":200,\"cookie\":\"fixture-refreshed\"}";
                case "/login/qr/key" -> "{\"code\":200,\"data\":{\"code\":200,\"unikey\":\"fixture-key\"}}";
                case "/login/qr/create" -> "{\"code\":200,\"data\":{\"qrurl\":\"https://music.163.com/login?codekey=fixture-key\",\"qrimg\":\"data:image/png;base64,fixture\"}}";
                case "/login/qr/check" -> "{\"code\":" + fixture.qrCode + (fixture.qrCode == 803 && !fixture.noCookie
                    ? ",\"cookie\":\"fixture-qr\"" : "") + "}";
                default -> throw new IOException("Unexpected auth endpoint");
            };
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        var api = new NeteaseMusicApi(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        try {
            storeChecks(root);
            int before = fixture.requests.get();
            expect("music.login.error.input", () -> api.sendCaptcha("not-a-phone"));
            expect("music.login.error.input", () -> api.phoneLogin("13800000000", "oops"));
            require(fixture.requests.get() == before, "Invalid input sent a login request");
            api.sendCaptcha("13800000000");
            require(fixture.last.get("phone").equals("13800000000"), "Captcha phone was not encoded in the body");
            var account = api.phoneLogin("13800000000", "123456");
            require(account.authenticated() && account.userId().equals("201") && account.phone().equals("13800000000"), "Phone login lost session/profile");
            require(fixture.last.get("captcha").equals("123456"), "Captcha was not encoded in the body");
            fixture.fallback = true;
            require(api.phoneLogin("13800000000", "123456").userId().equals("303"), "Missing login profile did not resolve the authenticated account");
            require(fixture.last.get("cookie").equals("fixture-phone"), "Fallback account request lost login cookie");
            fixture.fallback = false; fixture.reject = true;
            expect("music.login.error.response", () -> api.phoneLogin("13800000000", "123456"));
            fixture.reject = false;
            require(api.createLoginQr().image().startsWith("data:image/png"), "QR image schema lost");
            fixture.qrCode = 803; fixture.noCookie = true;
            expect("music.login.error.response", () -> api.checkLoginQr("fixture-key"));
            fixture.noCookie = false;

            var store = new MusicAccountStore(root.resolve("service/login_status.json")); store.save(OLD);
            var scheduled = new ArrayDeque<Runnable>();
            var errors = new ArrayList<String>();
            var sessions = new ArrayList<MusicAccount>();
            var states = new ArrayList<MusicLoginService.QrState>();
            var service = new MusicLoginService(api, store, Runnable::run, Runnable::run, (task, delay) -> {
                require(delay == 3_000, "QR polling interval changed"); scheduled.add(task);
            });
            service.refresh(sessions::add, error -> errors.add(error.key()));
            require(service.account().cookie().equals("fixture-refreshed"), "Refresh did not update active cookie");
            require(new MusicAccountStore(root.resolve("service/login_status.json")).snapshot().cookie().equals("fixture-refreshed"), "Refreshed cookie was not persisted");
            fixture.failure = "/login/status";
            service.check(sessions::add, error -> errors.add(error.key()));
            require(service.account().authenticated() && errors.removeFirst().equals("music.error.network"), "Network outage discarded a valid local account");
            fixture.failure = ""; fixture.anonymous = true;
            service.check(sessions::add, error -> errors.add(error.key()));
            require(!service.account().authenticated() && !sessions.getLast().authenticated(), "Anonymous code=200 response kept an authenticated account");
            require(!new MusicAccountStore(root.resolve("service/login_status.json")).snapshot().authenticated(), "Expired session returned after restart");
            fixture.anonymous = false;
            service.phoneLogin("13800000000", "123456", sessions::add, error -> errors.add(error.key()));
            require(service.account().owner().equals("netease:201"), "Player account owner lost phone login");
            fixture.failure = "/logout";
            service.logout(ignored -> { }, error -> errors.add(error.key()));
            require(!service.account().authenticated() && errors.isEmpty(), "Offline logout retained the local session");
            fixture.failure = "";
            fixture.qrCode = 801;
            service.qrLogin(code -> { }, states::add, sessions::add, error -> errors.add(error.key()));
            scheduled.remove().run(); fixture.qrCode = 802;
            scheduled.remove().run(); scheduled.remove().run(); fixture.qrCode = 803;
            scheduled.remove().run();
            require(states.equals(List.of(MusicLoginService.QrState.WAITING, MusicLoginService.QrState.SCANNED)), "Repeated scan notifications or missing QR state");
            require(service.account().userId().equals("303") && service.account().phone().isBlank(), "QR login reused the previous account or phone");
            require(fixture.last.get("cookie").equals("fixture-qr"), "QR account lookup used the old global cookie");
            service.qrLogin(code -> { }, states::add, sessions::add, error -> errors.add(error.key()));
            service.logout(ignored -> { }, error -> errors.add(error.key()));
            before = fixture.requests.get(); scheduled.remove().run();
            require(fixture.requests.get() == before && !service.account().authenticated(), "Old QR poll logged back in after logout");
            fixture.qrCode = 800;
            service.qrLogin(code -> { }, states::add, sessions::add, error -> errors.add(error.key())); scheduled.remove().run();
            require(errors.removeFirst().equals("music.login.error.qrexpired") && scheduled.isEmpty(), "Expired QR kept polling");
            fixture.qrCode = 801;
            service.qrLogin(code -> { }, states::add, sessions::add, error -> errors.add(error.key()));
            for (int i = 0; i < 60; i++) scheduled.remove().run();
            require(errors.removeFirst().equals("music.login.error.qrtimeout") && scheduled.isEmpty(), "QR timeout did not stop polling");
            service.qrLogin(code -> { }, states::add, sessions::add, error -> errors.add(error.key()));
            service.close(); before = fixture.requests.get(); scheduled.remove().run();
            require(fixture.requests.get() == before && errors.isEmpty(), "Shutdown dispatched stale login work");

            raceChecks(api, fixture, root);
            requestChecks(api, root);
            System.out.println("Music account/request checks passed: " + checks.get() + " assertions; legacy sessions, "
                + "POST auth, refresh, offline logout, QR lifecycle and stale in-flight login replies.");
        } finally {
            fixture.release.countDown(); fixture.statusRelease.countDown(); server.stop(0); executor.close();
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void raceChecks(NeteaseMusicApi api, Fixture fixture, Path root) throws Exception {
        var store = new MusicAccountStore(root.resolve("race/login_status.json")); store.save(OLD);
        var completions = new AtomicInteger(); var errors = new AtomicInteger();
        var workers = new ArrayDeque<Runnable>(); var callbacks = new ArrayDeque<Runnable>();
        var service = new MusicLoginService(api, store, workers::add, callbacks::add, (task, delay) -> { });
        fixture.block = true;
        service.phoneLogin("13800000001", "123456", account -> completions.incrementAndGet(), error -> errors.incrementAndGet());
        Thread pending = Thread.ofVirtual().start(workers.remove());
        require(fixture.entered.await(5, TimeUnit.SECONDS), "Fixture did not enter the in-flight login");
        service.logout(ignored -> completions.incrementAndGet(), error -> errors.incrementAndGet());
        workers.remove().run();
        fixture.release.countDown(); pending.join(5_000);
        require(!pending.isAlive(), "In-flight login fixture did not complete");
        while (!callbacks.isEmpty()) callbacks.remove().run();
        require(!service.account().authenticated() && completions.get() == 1 && errors.get() == 0, "Stale HTTP login overwrote logout or emitted a success");
        fixture.block = false;
        service.phoneLogin("13800000000", "123456", account -> completions.incrementAndGet(), error -> errors.incrementAndGet());
        workers.remove().run();
        service.logout(ignored -> completions.incrementAndGet(), error -> errors.incrementAndGet()); workers.remove().run();
        while (!callbacks.isEmpty()) callbacks.remove().run();
        require(completions.get() == 2 && !service.account().authenticated(), "A queued login success survived a newer logout");
        store.save(OLD); fixture.blockStatus = true;
        var statuses = new ArrayList<MusicAccount>();
        service.check(statuses::add, error -> errors.incrementAndGet());
        Thread oldStatus = Thread.ofVirtual().start(workers.remove());
        require(fixture.statusEntered.await(5, TimeUnit.SECONDS), "Fixture did not enter the old status request");
        fixture.anonymous = true; service.check(statuses::add, error -> errors.incrementAndGet()); workers.remove().run();
        fixture.statusRelease.countDown(); oldStatus.join(5_000);
        require(!oldStatus.isAlive(), "Status fixture did not complete");
        while (!callbacks.isEmpty()) callbacks.remove().run();
        require(!service.account().authenticated() && statuses.size() == 2 && statuses.stream().noneMatch(MusicAccount::authenticated),
            "Old valid status response restored an expired account");
        fixture.blockStatus = false; fixture.anonymous = false;
        service.close();
    }

    private static void storeChecks(Path root) throws Exception {
        Path file = root.resolve("legacy/login_status.json"); Files.createDirectories(file.getParent());
        JsonObject legacy = new JsonObject(); legacy.addProperty("cookie", OLD.cookie()); legacy.addProperty("userId", OLD.userId());
        legacy.addProperty("nickname", OLD.nickname()); legacy.addProperty("phone", OLD.phone()); legacy.addProperty("savedTime", System.currentTimeMillis());
        Files.writeString(file, legacy.toString());
        var store = new MusicAccountStore(file);
        require(store.snapshot().equals(OLD), "Existing login_status.json was not compatible");
        require(!OLD.toString().contains(OLD.cookie()) && !OLD.toString().contains(OLD.phone()), "Account diagnostics exposed credentials");
        store.save(OLD);
        require(new MusicAccountStore(file).snapshot().equals(OLD), "Unicode account metadata did not survive restart");
        Files.delete(file); Files.createDirectory(file); Files.writeString(file.resolve("marker"), "fixture");
        try { store.save(MusicAccount.GUEST); throw new AssertionError("Expected save failure"); }
        catch (IOException expected) { require(store.snapshot().equals(OLD), "Failed account save changed in-memory state"); }
        require(Files.readString(file.resolve("marker")).equals("fixture"), "Account save failure destroyed destination");
        try (var paths = Files.list(file.getParent())) { require(paths.noneMatch(path -> path.toString().endsWith(".tmp")), "Failed save leaked a temporary session file"); }
        Path expired = root.resolve("expired.json"); legacy.addProperty("savedTime", System.currentTimeMillis() - 8 * 24 * 60 * 60 * 1000L);
        Files.writeString(expired, legacy.toString());
        require(!new MusicAccountStore(expired).snapshot().authenticated(), "Expired legacy session was loaded");
        Path malformed = root.resolve("malformed.json"); Files.writeString(malformed, "{invalid}");
        require(!new MusicAccountStore(malformed).snapshot().authenticated(), "Malformed session prevented guest fallback");
    }

    private static void requestChecks(NeteaseMusicApi api, Path root) throws Exception {
        var providers = new MusicProviders(new MusicLibraryStore(root.resolve("requests")), new NeteaseMusicProvider(api), new AudiusMusicProvider());
        I18n.setLanguage(Language.ENGLISH);
        var target = MusicRequest.target(providers, "42", null);
        providers.select("audius");
        require(target.provider().id().equals("netease") && target.quality().equals("exhigh"), "Pending request changed with provider selection");
        require(MusicRequest.target(providers, "42", null).quality().equals("standard"), "Unqualified request lost selected provider default");
        require(MusicRequest.target(providers, "NETEASE:42", "LOSSLESS").quality().equals("lossless"), "Qualified request or quality aliases changed");
        I18n.setLanguage(Language.CHINESE);
        require(MusicRequest.target(providers, "netease:42", MusicText.get("music.quality.exhigh")).quality().equals("exhigh"), "Translated quality is no longer accepted");
        expect("music.error.quality", () -> MusicRequest.target(providers, "audius:42", "lossless"));
        expect("music.error.metadata", () -> MusicRequest.target(providers, "../42", null));
        expect("music.error.provider", () -> MusicRequest.target(providers, "unknown:42", null));
        for (Language language : List.of(Language.ENGLISH, Language.CHINESE)) {
            I18n.setLanguage(language);
            require(!MusicText.get("music.login.error.qrtimeout").startsWith("music.") && !MusicText.get("music.login.command.phone").startsWith("music."), "Login translation missing");
            for (String action : List.of("send", "phone", "qr", "status", "refresh", "logout", "help"))
                require(MusicText.get("music.login.command." + action).startsWith(".login " + action), "Translated help lost command syntax");
        }
    }

    private static String profile(int id) { return ",\"account\":{\"id\":" + id + "},\"profile\":{\"userId\":" + id + ",\"nickname\":\"Fixture 用户\"}"; }
    private static Map<String, String> decode(String body) {
        var result = new java.util.HashMap<String, String>();
        for (String pair : body.split("&")) { String[] parts = pair.split("=", 2); result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8)); }
        return result;
    }
    private static void require(boolean condition, String message) { checks.incrementAndGet(); if (!condition) throw new AssertionError(message); }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static void expect(String key, Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected " + key); }
        catch (MusicError error) { require(error.key().equals(key), "Unexpected error: " + error.key()); }
    }
    private static final class Fixture {
        final AtomicInteger requests = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final CountDownLatch statusEntered = new CountDownLatch(1), statusRelease = new CountDownLatch(1);
        volatile Map<String, String> last = Map.of();
        volatile String failure = "";
        volatile boolean anonymous, reject, fallback, noCookie, block, blockStatus;
        volatile int qrCode = 801;
    }
}
