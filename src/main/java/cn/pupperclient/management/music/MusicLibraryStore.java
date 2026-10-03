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
    public record Playlist(String id, String name, List<MusicQueue.Entry> entries, long revision) {
        public Playlist { entries = List.copyOf(entries); }
    }
    public record Favorite(MusicTrack track, String filename) {
        public String key() { return key(track, filename); }
        public static String key(MusicTrack track, String filename) {
            return track.remote() ? "song:" + track.key() : "file:" + filename;
        }
    }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final class State {
        String provider = "netease";
        String repeatMode = "off";
        Map<String, Playlist> playlists = new LinkedHashMap<>();
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
                if (state.playlists == null) state.playlists = new LinkedHashMap<>();
                // Old indexes used song:<numeric-id>; re-key values while retaining account ownership.
                state.favorites.replaceAll((owner, favorites) -> {
                    Map<String, Favorite> migrated = new LinkedHashMap<>();
                    favorites.values().stream().filter(f -> f != null && f.track() != null)
                        .forEach(f -> migrated.put(f.key(), f));
                    return migrated;
                });
            } catch (RuntimeException malformed) {
                // Preserve the damaged index for recovery instead of silently overwriting it.
                Files.copy(index, index.resolveSibling(index.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                state = new State();
            }
        }
    }

    public synchronized MusicTrack metadata(String filename) { return state.downloads.get(filename); }
    /** Catalogue refreshes preserve the actual file's access state, including a concurrent preview-to-full upgrade. */
    public synchronized boolean refreshMetadata(String filename, MusicTrack catalogue) throws IOException {
        MusicTrack current = state.downloads.get(filename);
        if (current == null || !current.sameSong(catalogue)) return false;
        MusicTrack updated = catalogue.withAccess(catalogue.fee(), current.previewMillis());
        if (updated.equals(current)) return false;
        register(filename, updated); return true;
    }
    public synchronized String provider() { return state.provider == null ? "netease" : state.provider; }
    public synchronized void provider(String provider) throws IOException { change(next -> next.provider = provider); }
    public synchronized MusicRepeatMode repeatMode() { return MusicRepeatMode.parse(state.repeatMode); }
    public synchronized void repeatMode(MusicRepeatMode mode) throws IOException { change(next -> next.repeatMode = mode.name()); }
    public synchronized List<Playlist> playlists() { return List.copyOf(state.playlists.values()); }
    public synchronized Playlist playlist(String id) { return state.playlists.get(id); }
    public synchronized Playlist createPlaylist(String name) throws IOException {
        return createPlaylist(name, List.of());
    }
    public synchronized Playlist createPlaylist(String name, List<MusicQueue.Entry> entries) throws IOException {
        String normalized = playlistName(name);
        Map<String, MusicQueue.Entry> unique = new LinkedHashMap<>(); entries.forEach(entry -> unique.putIfAbsent(entry.key(), entry));
        Playlist playlist = new Playlist(java.util.UUID.randomUUID().toString(), normalized, List.copyOf(unique.values()), 0);
        change(next -> next.playlists.put(playlist.id(), playlist));
        return playlist;
    }
    public synchronized void renamePlaylist(String id, String name) throws IOException {
        Playlist current = requirePlaylist(id); String normalized = playlistName(name);
        change(next -> next.playlists.put(id, new Playlist(id, normalized, current.entries(), current.revision() + 1)));
    }
    public synchronized void deletePlaylist(String id) throws IOException { change(next -> next.playlists.remove(id)); }
    public synchronized void addToPlaylist(String id, List<MusicQueue.Entry> entries) throws IOException {
        Playlist current = requirePlaylist(id);
        Map<String, MusicQueue.Entry> unique = new LinkedHashMap<>();
        current.entries().forEach(entry -> unique.put(entry.key(), entry));
        entries.forEach(entry -> unique.putIfAbsent(entry.key(), entry));
        change(next -> next.playlists.put(id, new Playlist(id, current.name(), List.copyOf(unique.values()), current.revision() + 1)));
    }
    public synchronized boolean removeFromPlaylist(String id, String key, long revision) throws IOException {
        Playlist current = requirePlaylist(id);
        if (current.revision() != revision || current.entries().stream().noneMatch(entry -> entry.key().equals(key))) return false;
        change(next -> next.playlists.put(id, new Playlist(id, current.name(), current.entries().stream()
            .filter(entry -> !entry.key().equals(key)).toList(), current.revision() + 1)));
        return true;
    }
    public synchronized boolean moveInPlaylist(String id, int from, int to, long revision) throws IOException {
        Playlist current = requirePlaylist(id);
        if (current.revision() != revision || from < 0 || to < 0 || from >= current.entries().size() || to >= current.entries().size()) return false;
        var entries = new java.util.ArrayList<>(current.entries()); entries.add(to, entries.remove(from));
        change(next -> next.playlists.put(id, new Playlist(id, current.name(), entries, current.revision() + 1)));
        return true;
    }
    private Playlist requirePlaylist(String id) {
        Playlist playlist = state.playlists.get(id);
        if (playlist == null) throw new IllegalArgumentException("Unknown playlist");
        return playlist;
    }
    private static String playlistName(String name) {
        String normalized = name == null ? "" : name.strip();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 80 || normalized.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid playlist name");
        return normalized;
    }

    public synchronized Path downloaded(long id) {
        return downloaded(new MusicTrack(id, "", "", "", "", 0));
    }

    public synchronized Path downloaded(MusicTrack track) {
        for (var entry : state.downloads.entrySet()) {
            Path path = directory.resolve(entry.getKey()).normalize();
            if (entry.getValue().sameSong(track) && path.getParent().equals(directory) && Files.isRegularFile(path)) return path;
        }
        return null;
    }

    public synchronized void register(String filename, MusicTrack track) throws IOException {
        register(filename, track, filename);
    }

    public synchronized void register(String filename, MusicTrack track, String previousFilename) throws IOException {
        if (!Path.of(filename).getFileName().toString().equals(filename)) throw new IOException("Invalid music filename");
        change(next -> {
            next.downloads.entrySet().removeIf(e -> e.getValue().sameSong(track) && !e.getKey().equals(filename));
            next.downloads.put(filename, track);
            next.playlists.replaceAll((id, playlist) -> {
                Map<String, MusicQueue.Entry> updated = new LinkedHashMap<>();
                for (MusicQueue.Entry entry : playlist.entries()) {
                    MusicQueue.Entry value = entry.track().sameSong(track) || !previousFilename.isEmpty() && entry.filename().equals(previousFilename)
                        ? new MusicQueue.Entry(track, filename) : entry;
                    updated.putIfAbsent(value.key(), value);
                }
                List<MusicQueue.Entry> entries = List.copyOf(updated.values());
                return entries.equals(playlist.entries()) ? playlist : new Playlist(id, playlist.name(), entries, playlist.revision() + 1);
            });
            for (Map<String, Favorite> favorites : next.favorites.values()) {
                List<Favorite> matches = favorites.values().stream().filter(f -> f.track().sameSong(track)
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

    /** A shared Favorites page retains each source's own account, including local-file ownership. */
    public synchronized List<Favorite> favorites(Map<String, String> sourceOwners) {
        return sourceOwners.entrySet().stream().flatMap(source -> favorites(source.getValue()).stream()
            .filter(f -> f.track().provider().equals(source.getKey()))).toList();
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
        replaceCloudLikes(owner, "netease", tracks);
    }

    public synchronized void replaceCloudLikes(String owner, String provider, List<MusicTrack> tracks) throws IOException {
        change(next -> {
            Map<String, Favorite> favorites = next.favorites.computeIfAbsent(owner, _ -> new LinkedHashMap<>());
            favorites.entrySet().removeIf(e -> e.getValue().track().provider().equals(provider));
            for (MusicTrack track : tracks) favorites.put(Favorite.key(track, ""), new Favorite(track, ""));
        });
    }

    private void change(Consumer<State> mutation) throws IOException {
        State previous = state;
        State next = new State();
        next.provider = previous.provider;
        next.repeatMode = previous.repeatMode;
        next.playlists.putAll(previous.playlists);
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
