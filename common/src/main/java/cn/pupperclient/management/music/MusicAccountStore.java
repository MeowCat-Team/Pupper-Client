package cn.pupperclient.management.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Reads the existing login_status.json schema and publishes sessions only after a successful save. */
public final class MusicAccountStore {
    public record BoundSession(MusicAccount account, java.net.URI endpoint) { }
    private final Path file;
    private MusicAccount account;
    private String origin = "";
    private MusicApiConfiguration configuration;

    public MusicAccountStore(Path file) { this.file = file.toAbsolutePath().normalize(); }
    public MusicAccountStore(Path file, MusicApiConfiguration configuration) {
        this(file); bind(configuration);
    }
    synchronized void bind(MusicApiConfiguration configuration) { this.configuration = configuration; }
    public synchronized BoundSession boundSnapshot() {
        MusicAccount visible = snapshot();
        return new BoundSession(visible, origin.isBlank() ? null : java.net.URI.create(origin));
    }

    public synchronized MusicAccount snapshot() {
        if (account != null) return visible();
        account = MusicAccount.GUEST;
        if (!Files.isRegularFile(file)) return account;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            long saved = json.get("savedTime").getAsLong();
            if (System.currentTimeMillis() - saved > 7 * 24 * 60 * 60 * 1000L) return account;
            MusicAccount loaded = new MusicAccount(text(json, "cookie"), text(json, "userId"),
                text(json, "nickname"), text(json, "phone"));
            // The legacy implementation used this single fixed endpoint. Never migrate it to a new server.
            String storedOrigin = text(json, "origin");
            origin = MusicApiConfiguration.validate(storedOrigin.isBlank() ? NeteaseMusicApi.DEFAULT_ORIGIN : java.net.URI.create(storedOrigin)).toString();
            if (loaded.authenticated()) account = loaded;
        } catch (IOException | RuntimeException invalid) {
            // An unreadable/old file must not prevent startup or expose credentials in logs.
        }
        return visible();
    }

    public synchronized void save(MusicAccount next) throws IOException {
        JsonObject json = new JsonObject();
        if (next.authenticated()) {
            json.addProperty("cookie", next.cookie()); json.addProperty("userId", next.userId());
            json.addProperty("nickname", next.nickname()); json.addProperty("phone", next.phone());
            json.addProperty("origin", configuration == null ? NeteaseMusicApi.DEFAULT_ORIGIN.toString() : configuration.snapshot().endpoint().toString());
        }
        json.addProperty("savedTime", System.currentTimeMillis());
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".pupper-login-", ".tmp");
        try {
            Files.writeString(temporary, json.toString(), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            account = next.authenticated() ? next : MusicAccount.GUEST;
            origin = next.authenticated() ? json.get("origin").getAsString() : "";
        } finally { Files.deleteIfExists(temporary); }
    }

    private MusicAccount visible() {
        if (configuration == null) return account;
        var endpoint = configuration.snapshot();
        return endpoint.trustedAccounts() && endpoint.endpoint().toString().equals(origin) ? account : MusicAccount.GUEST;
    }

    private static String text(JsonObject json, String key) {
        var value = json.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
}
