package cn.pupperclient.management.music;

import cn.pupperclient.management.command.impl.LoginCommand;
import cn.pupperclient.utils.file.FileLocation;
import cn.pupperclient.utils.thread.Multithreading;
import cn.pupperclient.management.music.lyric.LyricsManager;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared asynchronous operations for the command, player and HUD, routed by each track's source. */
public final class MusicService {
    public static final List<String> QUALITIES = NeteaseMusicProvider.QUALITIES;
    private static final Pattern LEGACY = Pattern.compile("music_(\\d+)\\.(mp3|flac)", Pattern.CASE_INSENSITIVE);
    private record Account(String owner, String cookie, String userId) { }
    @FunctionalInterface private interface Operation<T> { T run() throws Exception; }
    private final MusicManager manager;
    private final MusicLibraryStore library;
    private final NeteaseMusicApi netease;
    private final MusicProviders providers;
    private final Map<String, MusicDownload> downloads = new ConcurrentHashMap<>();
    private final LyricsManager lyrics;
    private final Map<String, Integer> progress = new ConcurrentHashMap<>();
    private final Set<String> coversLoading = ConcurrentHashMap.newKeySet();
    private final Set<String> favoritesLoading = ConcurrentHashMap.newKeySet();

    public MusicService(MusicManager manager, MusicLibraryStore library) {
        this.manager = manager;
        this.library = library;
        netease = new NeteaseMusicApi(URI.create(LoginCommand.getApiBase()));
        providers = new MusicProviders(library, new NeteaseMusicProvider(netease), new AudiusMusicProvider());
        for (MusicProvider provider : providers.all()) downloads.put(provider.id(), new MusicDownload(provider, library,
            FileLocation.MUSIC_DIR.toPath(), FileLocation.CACHE_DIR.toPath()));
        lyrics = new LyricsManager(track -> providers.get(track.provider()).lyrics(track),
            FileLocation.CACHE_DIR.toPath(), Multithreading::runAsync);
    }

    public LyricsManager lyrics() { return lyrics; }
    public MusicProvider provider() { return providers.selected(); }
    public List<MusicProvider> providers() { return providers.all(); }
    public void selectProvider(String id) throws MusicError, IOException { providers.select(id); }
    public List<String> qualities() { return provider().qualities(); }
    public List<String> qualities(String reference) {
        try { return providers.forReference(reference).qualities(); }
        catch (MusicError invalid) { return List.of(); }
    }
    public String defaultQuality(MusicTrack track) {
        try { return providers.get(track.provider()).defaultQuality(); }
        catch (MusicError invalid) { return "standard"; }
    }

    public void search(String keyword, int offset, Consumer<MusicProvider.SearchResult> success, Consumer<MusicError> failure) {
        search(keyword, 30, offset, success, failure);
    }
    public void search(String keyword, int limit, int offset, Consumer<MusicProvider.SearchResult> success,
            Consumer<MusicError> failure) {
        MusicProvider selected = provider();
        task(() -> selected.search(keyword, limit, offset), success, failure);
    }

    public void download(long id, String quality, boolean play, Consumer<Music> success, Consumer<MusicError> failure) {
        resolve("netease:" + id, track -> download(track, quality, play, success, failure), failure);
    }
    public void resolve(String reference, Consumer<MusicTrack> success, Consumer<MusicError> failure) {
        String[] parts = reference.split(":", 2);
        MusicProvider source;
        try { source = providers.forReference(reference); }
        catch (MusicError invalid) { failure.accept(invalid); return; }
        String trackId = parts.length == 2 ? parts[1] : parts[0];
        task(() -> source.track(trackId), success, failure);
    }

    public void download(MusicTrack track, String quality, boolean play, Consumer<Music> success, Consumer<MusicError> failure) {
        acquire(track, quality, play, false, success, failure);
    }
    public void play(MusicTrack track, String quality, Consumer<Music> success, Consumer<MusicError> failure) {
        manager.playFrom(List.of(new MusicQueue.Entry(track, "")), 0, quality, success, failure);
    }
    /** Playback buffers never publish a library entry; saving is an explicit download action. */
    public void prepare(MusicTrack track, String quality, Consumer<Music> success, Consumer<MusicError> failure) {
        Music local = local(track);
        if (local != null) { success.accept(local); return; }
        acquire(track, quality, false, true, success, failure);
    }
    private void acquire(MusicTrack track, String quality, boolean play, boolean temporary, Consumer<Music> success,
            Consumer<MusicError> failure) {
        MusicDownload download = downloads.get(track.provider());
        if (download == null) { failure.accept(new MusicError("music.error.provider")); return; }
        if (progress.putIfAbsent(track.key(), 0) != null) { failure.accept(new MusicError("music.error.busy")); return; }
        Account account = account(track.provider());
        Music playing = manager.getCurrentMusic();
        Path playingFile = playing == null ? null : playing.getAudio().toPath().toAbsolutePath().normalize();
        task(() -> {
            try {
                MusicDownload.Result result = temporary
                    ? download.playback(track, quality, account.cookie(), playingFile, value -> progress.put(track.key(), value))
                    : download.download(track, quality, account.cookie(), playingFile, value -> progress.put(track.key(), value));
                if (temporary) return new Music(result.audio().toFile(), result.track().title(), result.track().artist(),
                    java.nio.file.Files.isRegularFile(download.cover(track)) ? download.cover(track).toFile() : null,
                    Color.BLACK, result.track());
                manager.load();
                return manager.getMusics().stream().filter(m -> m.getAudio().toPath().toAbsolutePath().normalize()
                    .equals(result.audio())).findFirst().orElseThrow(() -> new MusicError("music.error.file"));
            } finally { progress.remove(track.key()); }
        }, music -> { if (play) manager.play(music); success.accept(music); }, failure);
    }

    public int downloadProgress(MusicTrack track) { return progress.getOrDefault(track.key(), -1); }
    public Music local(MusicTrack track) {
        return manager.getMusics().stream().filter(m -> m.getTrack().sameSong(track)).findFirst().orElse(null);
    }
    public File cover(MusicTrack track) {
        MusicDownload download = downloads.get(track.provider());
        if (download == null || !track.remote()) return null;
        Path cover = download.cover(track);
        if (java.nio.file.Files.isRegularFile(cover)) return cover.toFile();
        if (!track.coverUrl().isBlank() && coversLoading.add(track.key()))
            Multithreading.runAsync(() -> download.fetchCover(track));
        return null;
    }

    public void refresh(Consumer<Integer> success, Consumer<MusicError> failure) {
        coversLoading.clear();
        lyrics.clearCache();
        task(() -> {
            manager.load();
            List<Music> legacy = manager.getMusics().stream().filter(m -> !m.getTrack().remote()
                && LEGACY.matcher(m.getAudio().getName()).matches()).toList();
            if (legacy.isEmpty()) return 0;
            List<Long> ids = legacy.stream().map(m -> {
                Matcher match = LEGACY.matcher(m.getAudio().getName()); match.matches();
                return Long.parseLong(match.group(1));
            }).toList();
            List<MusicTrack> tracks = netease.details(ids);
            for (MusicTrack track : tracks) {
                Music playing = manager.getCurrentMusic();
                downloads.get("netease").download(track, "exhigh", null, playing == null ? null
                    : playing.getAudio().toPath().toAbsolutePath().normalize(), _ -> { });
            }
            manager.load();
            return tracks.size();
        }, success, failure);
    }

    public boolean loggedIn() { return loggedIn(provider().id()); }
    public boolean loggedIn(String source) { return source.equals("netease") && !account(source).owner().equals("guest"); }
    public boolean isLiked(MusicTrack track, String filename) { return library.isLiked(account(track.provider()).owner(), track, filename); }
    public List<MusicLibraryStore.Favorite> favorites() {
        Map<String, String> owners = new java.util.LinkedHashMap<>();
        for (MusicProvider source : providers.all()) owners.put(source.id(), account(source.id()).owner());
        owners.put("local", account("local").owner());
        return library.favorites(owners);
    }
    public boolean favoritesBusy() { return favoritesLoading.contains(account(provider().id()).owner()); }
    public boolean favoritesBusy(MusicTrack track) { return favoritesLoading.contains(account(track.provider()).owner()); }

    public void toggleLike(MusicTrack track, String filename, Consumer<Boolean> success, Consumer<MusicError> failure) {
        Account account = account(track.provider());
        if (!favoritesLoading.add(account.owner())) { failure.accept(new MusicError("music.error.busy")); return; }
        boolean liked = !library.isLiked(account.owner(), track, filename);
        task(() -> {
            try {
                if (track.remote() && !account.owner().equals("guest"))
                    providers.get(track.provider()).like(track, liked, account.cookie());
                library.setLiked(account.owner(), track, filename, liked);
                return liked;
            } finally { favoritesLoading.remove(account.owner()); }
        }, success, failure);
    }
    public void syncLikes(Consumer<Integer> success, Consumer<MusicError> failure) {
        syncLikes(provider().id(), success, failure);
    }
    public void syncLikes(String source, Consumer<Integer> success, Consumer<MusicError> failure) {
        MusicProvider selected;
        try { selected = providers.get(source); }
        catch (MusicError invalid) { failure.accept(invalid); return; }
        Account account = account(selected.id());
        if (!selected.cloudLikes() || account.owner().equals("guest")) { failure.accept(new MusicError("music.error.login")); return; }
        if (!favoritesLoading.add(account.owner())) { failure.accept(new MusicError("music.error.busy")); return; }
        task(() -> {
            try {
                List<MusicTrack> tracks = selected.likes(account.userId(), account.cookie());
                library.replaceCloudLikes(account.owner(), selected.id(), tracks);
                return tracks.size();
            } finally { favoritesLoading.remove(account.owner()); }
        }, success, failure);
    }
    private Account account(String provider) {
        if (!provider.equals("netease") && !provider.equals("local")) return new Account("guest", null, null);
        String cookie = LoginCommand.getCurrentCookie(), userId = LoginCommand.getCurrentUserId();
        return cookie == null || cookie.isBlank() || userId == null
            ? new Account("guest", null, null) : new Account("netease:" + userId, cookie, userId);
    }
    private static <T> void task(Operation<T> operation, Consumer<T> success, Consumer<MusicError> failure) {
        Multithreading.runAsync(() -> {
            try {
                T result = operation.run();
                Multithreading.runMainThread(() -> success.accept(result));
            } catch (Exception error) {
                MusicError translated = error instanceof MusicError musicError ? musicError
                    : new MusicError(error instanceof IOException ? "music.error.file" : "music.error.network");
                Multithreading.runMainThread(() -> failure.accept(translated));
            }
        });
    }
}
