package cn.pupperclient.management.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Sidecar metadata keeps provider names authoritative even when ID3 tags are absent or stale. */
public final class MusicLibraryStore {
    public record Favorite(MusicTrack track, String filename) {
        public String key() { return key(track, filename); }
        public static String key(MusicTrack track, String filename) {
            return track.id() > 0 ? "song:" + track.id() : "file:" + filename;
        }
    }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final class State {
        Map<String, MusicTrack> downloads = new LinkedHashMap<>();
        Map<String, Map<String, Favorite>> favorites = new LinkedHashMap<>();
    }
    private final Path directory;
    private final Path index;
    private State state;

    public MusicLibraryStore(Path directory) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        index = this.directory.resolve(".pupper-music.json");
        Files.createDirectories(this.directory);
        state = new State();
        if (Files.exists(index)) {
            try {
                State loaded = GSON.fromJson(Files.readString(index, StandardCharsets.UTF_8), State.class);
                if (loaded != null && loaded.downloads != null && loaded.favorites != null) state = loaded;
            } catch (RuntimeException malformed) {
                // Preserve the damaged index for recovery instead of silently overwriting it.
                Files.copy(index, index.resolveSibling(index.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public synchronized MusicTrack metadata(String filename) { return state.downloads.get(filename); }

    public synchronized Path downloaded(long id) {
        for (var entry : state.downloads.entrySet()) {
            Path path = directory.resolve(entry.getKey()).normalize();
            if (entry.getValue().id() == id && path.getParent().equals(directory) && Files.isRegularFile(path)) return path;
        }
        return null;
    }

    public synchronized void register(String filename, MusicTrack track) throws IOException {
        register(filename, track, filename);
    }

    public synchronized void register(String filename, MusicTrack track, String previousFilename) throws IOException {
        if (!Path.of(filename).getFileName().toString().equals(filename)) throw new IOException("Invalid music filename");
        change(next -> {
            next.downloads.entrySet().removeIf(e -> e.getValue().id() == track.id() && !e.getKey().equals(filename));
            next.downloads.put(filename, track);
            for (Map<String, Favorite> favorites : next.favorites.values()) {
                List<Favorite> matches = favorites.values().stream().filter(f -> f.track().id() == track.id()
                    || (!previousFilename.isEmpty() && f.filename().equals(previousFilename))).toList();
                for (Favorite favorite : matches) {
                    favorites.remove(favorite.key());
                    Favorite updated = new Favorite(track, filename);
                    favorites.put(updated.key(), updated);
                }
            }
        });
    }

    public synchronized boolean isLiked(String owner, MusicTrack track, String filename) {
        return state.favorites.getOrDefault(owner, Map.of()).containsKey(Favorite.key(track, filename));
    }

    public synchronized List<Favorite> favorites(String owner) {
        return List.copyOf(state.favorites.getOrDefault(owner, Map.of()).values());
    }

    public synchronized void setLiked(String owner, MusicTrack track, String filename, boolean liked) throws IOException {
        change(next -> {
            Map<String, Favorite> favorites = next.favorites.computeIfAbsent(owner, _ -> new LinkedHashMap<>());
            String key = Favorite.key(track, filename);
            if (liked) favorites.put(key, new Favorite(track, filename));
            else favorites.remove(key);
        });
    }

    public synchronized void replaceCloudLikes(String owner, List<MusicTrack> tracks) throws IOException {
        change(next -> {
            Map<String, Favorite> favorites = next.favorites.computeIfAbsent(owner, _ -> new LinkedHashMap<>());
            favorites.entrySet().removeIf(e -> e.getValue().track().id() > 0);
            for (MusicTrack track : tracks) favorites.put(Favorite.key(track, ""), new Favorite(track, ""));
        });
    }

    private void change(Consumer<State> mutation) throws IOException {
        State previous = state;
        State next = new State();
        next.downloads.putAll(previous.downloads);
        previous.favorites.forEach((owner, favorites) -> next.favorites.put(owner, new LinkedHashMap<>(favorites)));
        mutation.accept(next);
        state = next;
        try { save(); }
        catch (IOException failure) { state = previous; throw failure; }
    }

    private void save() throws IOException {
        Path temporary = Files.createTempFile(directory, ".music-index-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(state), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, index, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, index, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
