package cn.pupperclient.management.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Device-local listening preferences and recovery data, separate from account credentials. */
public final class MusicExperienceStore {
    public static final int MAX_RECENT = 200, MAX_QUEUE = 5000;
    public record Settings(float lyricSize, boolean translations, float lyricOffset, boolean gapless,
            float crossfadeSeconds, boolean normalize) {
        public static final Settings DEFAULT = new Settings(36, true, 0, true, 0, false);
        public Settings {
            lyricSize = finite(lyricSize, 36, 24, 48);
            lyricOffset = finite(lyricOffset, 0, -10, 10);
            crossfadeSeconds = finite(crossfadeSeconds, 0, 0, 12);
        }
        private static float finite(float value, float fallback, float min, float max) {
            return Float.isFinite(value) ? Math.clamp(value, min, max) : fallback;
        }
        public Settings lyrics(float size, boolean translated, float offset) {
            return new Settings(size, translated, offset, gapless, crossfadeSeconds, normalize);
        }
        public Settings audio(boolean seamless, float fade, boolean balanced) {
            return new Settings(lyricSize, translations, lyricOffset, seamless, fade, balanced);
        }
    }
    public record Recent(MusicQueue.Entry entry, long playedAt, int plays) { }
    public record Resume(List<MusicQueue.Entry> entries, double seconds, boolean shuffle, float volume) {
        public static final Resume EMPTY = new Resume(List.of(), 0, false, .5f);
        public Resume {
            entries = valid(entries, MAX_QUEUE);
            seconds = Double.isFinite(seconds) ? Math.clamp(seconds, 0, 7 * 86400) : 0;
            volume = Float.isFinite(volume) ? Math.clamp(volume, 0, 1) : .5f;
            if (entries.isEmpty()) seconds = 0;
        }
        public boolean available() { return !entries.isEmpty(); }
    }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final class State {
        Settings settings = Settings.DEFAULT;
        List<Recent> recent = List.of();
        Resume resume = Resume.EMPTY;
    }
    private final Path file;
    private State state;
    public MusicExperienceStore(Path directory) throws IOException {
        Files.createDirectories(directory);
        file = directory.toAbsolutePath().normalize().resolve(".pupper-listening.json");
        state = new State();
        if (Files.isRegularFile(file)) {
            try {
                State loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), State.class);
                if (loaded != null) {
                    state.settings = loaded.settings == null ? Settings.DEFAULT : loaded.settings;
                    state.resume = loaded.resume == null ? Resume.EMPTY : loaded.resume;
                    var unique = new LinkedHashMap<String, Recent>();
                    if (loaded.recent != null) for (Recent recent : loaded.recent) {
                        if (recent == null || !valid(recent.entry())) continue;
                        unique.putIfAbsent(recent.entry().key(), new Recent(recent.entry(), Math.max(0, recent.playedAt()),
                            Math.clamp(recent.plays(), 1, Integer.MAX_VALUE)));
                        if (unique.size() == MAX_RECENT) break;
                    }
                    state.recent = List.copyOf(unique.values());
                }
            } catch (RuntimeException malformed) {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                state = new State();
            }
        }
    }
    public synchronized Settings settings() { return state.settings; }
    public synchronized List<Recent> recent() { return state.recent; }
    public synchronized Resume resume() { return state.resume; }
    public synchronized void settings(Settings settings) throws IOException {
        Objects.requireNonNull(settings); if (!settings.equals(state.settings)) change(next -> next.settings = settings);
    }
    public synchronized void played(MusicQueue.Entry entry) throws IOException { played(entry, System.currentTimeMillis()); }
    public synchronized void played(MusicQueue.Entry entry, long at) throws IOException {
        if (!valid(entry)) throw new IllegalArgumentException("Invalid listening entry");
        var recent = new ArrayList<Recent>();
        int plays = state.recent.stream().filter(item -> item.entry().key().equals(entry.key())).mapToInt(Recent::plays).findFirst().orElse(0);
        recent.add(new Recent(entry, Math.max(0, at), plays == Integer.MAX_VALUE ? plays : plays + 1));
        state.recent.stream().filter(item -> !item.entry().key().equals(entry.key())).limit(MAX_RECENT - 1).forEach(recent::add);
        change(next -> next.recent = List.copyOf(recent));
    }
    public synchronized void clearRecent() throws IOException { change(next -> next.recent = List.of()); }
    public synchronized void resume(Resume resume) throws IOException {
        Objects.requireNonNull(resume); if (!resume.equals(state.resume)) change(next -> next.resume = resume);
    }
    private void change(Consumer<State> edit) throws IOException {
        State next = new State(); next.settings = state.settings; next.recent = state.recent; next.resume = state.resume;
        edit.accept(next);
        Path pending = Files.createTempFile(file.getParent(), ".listening-", ".tmp");
        try {
            Files.writeString(pending, GSON.toJson(next), StandardCharsets.UTF_8);
            try { Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING); }
            state = next;
        } finally { Files.deleteIfExists(pending); }
    }
    private static List<MusicQueue.Entry> valid(List<MusicQueue.Entry> entries, int limit) {
        return entries == null ? List.of() : entries.stream().filter(MusicExperienceStore::valid).limit(limit).toList();
    }
    private static boolean valid(MusicQueue.Entry entry) {
        if (entry == null || entry.track() == null) return false;
        String filename = entry.filename();
        if (filename.equals(".") || filename.equals("..") || filename.endsWith(".") || filename.endsWith(" ")) return false;
        for (int i = 0; i < filename.length(); i++) {
            char value = filename.charAt(i);
            if (value < 32 || "/\\:*?\"<>|".indexOf(value) >= 0) return false;
        }
        String base = filename.split("\\.", 2)[0].toUpperCase(java.util.Locale.ROOT);
        if (base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) return false;
        return entry.track().remote() || !filename.isBlank();
    }
}
