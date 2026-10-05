package cn.pupperclient.management.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/** Endpoint and explicit account trust only; this file never contains account credentials. */
public final class MusicApiConfiguration {
    public record Snapshot(URI endpoint, boolean trustedAccounts, long revision) { }
    @FunctionalInterface interface IoAction { void run() throws IOException; }
    private final Path file;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile Snapshot snapshot;

    public MusicApiConfiguration(Path file) {
        this.file = file.toAbsolutePath().normalize();
        snapshot = new Snapshot(NeteaseMusicApi.DEFAULT_ORIGIN, false, 0);
        if (!Files.isRegularFile(this.file)) return;
        try (var reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
            var data = JsonParser.parseReader(reader).getAsJsonObject();
            snapshot = new Snapshot(validate(URI.create(data.get("endpoint").getAsString())),
                data.has("trustedAccounts") && data.get("trustedAccounts").getAsBoolean(), 0);
        } catch (IOException | RuntimeException invalid) {
            // Invalid or old settings retain anonymous defaults, without echoing their contents.
        }
    }
    public MusicApiConfiguration(URI endpoint, boolean trustedAccounts) {
        file = null; snapshot = new Snapshot(validate(endpoint), trustedAccounts, 0);
    }
    public Snapshot snapshot() { return snapshot; }
    public MusicPreparation.Registration onChange(Runnable listener) {
        listeners.add(listener); return () -> listeners.remove(listener);
    }
    public void configure(URI endpoint, boolean trustedAccounts) throws IOException {
        URI checked = validate(endpoint);
        synchronized (this) {
            if (snapshot.endpoint().equals(checked) && snapshot.trustedAccounts() == trustedAccounts) return;
            if (file != null) {
                JsonObject data = new JsonObject(); data.addProperty("endpoint", checked.toString());
                data.addProperty("trustedAccounts", trustedAccounts);
                Files.createDirectories(file.getParent());
                Path temporary = Files.createTempFile(file.getParent(), ".pupper-music-api-", ".tmp");
                try {
                    Files.writeString(temporary, data.toString(), StandardCharsets.UTF_8);
                    try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                    catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
                } finally { Files.deleteIfExists(temporary); }
            }
            snapshot = new Snapshot(checked, trustedAccounts, snapshot.revision() + 1);
        }
        for (Runnable listener : listeners) try { listener.run(); }
        catch (RuntimeException ignored) { /* A consumer cannot undo the persisted endpoint or suppress other invalidations. */ }
    }
    /** Makes a session publication atomic with an endpoint change. No consumer callbacks run here. */
    synchronized boolean guard(long revision, IoAction action) throws IOException {
        if (snapshot.revision() != revision) return false;
        action.run(); return true;
    }
    public static URI validate(URI value) {
        if (value == null || value.isOpaque() || value.getHost() == null || value.getRawUserInfo() != null
                || value.getRawQuery() != null || value.getRawFragment() != null || value.getPort() == 0
                || value.getPort() > 65535) throw new IllegalArgumentException("Invalid music API endpoint");
        String scheme = value.getScheme() == null ? "" : value.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(scheme.equals("http") && loopback(value)))
            throw new IllegalArgumentException("Remote music APIs require HTTPS");
        String path = value.normalize().getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (!path.startsWith("/") || path.startsWith("//") || path.toLowerCase(Locale.ROOT).contains("%2f")
                || path.toLowerCase(Locale.ROOT).contains("%5c") || path.toLowerCase(Locale.ROOT).contains("%2e")
                || path.contains("..")) throw new IllegalArgumentException("Invalid music API endpoint path");
        if (!path.endsWith("/")) path += "/";
        int port = value.getPort();
        if ((scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80)) port = -1;
        String host = value.getHost().toLowerCase(Locale.ROOT);
        return URI.create(scheme + "://" + host + (port < 0 ? "" : ":" + port) + path);
    }
    public static boolean loopback(URI uri) {
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.equals("::1") || host.equals("[::1]")) return true;
        String[] octets = host.split("\\.", -1);
        if (octets.length != 4 || !octets[0].equals("127")) return false;
        for (String octet : octets) {
            if (!octet.matches("[0-9]{1,3}") || Integer.parseInt(octet) > 255) return false;
        }
        return true;
    }
}
