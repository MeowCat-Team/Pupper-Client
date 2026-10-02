package cn.pupperclient.management.music;

import cn.pupperclient.management.command.impl.LoginCommand;
import cn.pupperclient.utils.file.FileLocation;
import cn.pupperclient.utils.thread.Multithreading;
import cn.pupperclient.management.music.lyric.LyricsManager;
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

/** Shared asynchronous operations for the command, player and HUD. */
public final class MusicService {
    public static final List<String> QUALITIES = List.of("standard", "higher", "exhigh", "lossless", "hires",
        "jyeffect", "sky", "dolby", "jymaster");
    private static final Pattern LEGACY = Pattern.compile("music_(\\d+)\\.(mp3|flac)", Pattern.CASE_INSENSITIVE);
    private record Account(String owner, String cookie, String userId) { }
    @FunctionalInterface private interface Operation<T> { T run() throws Exception; }
    private final MusicManager manager;
    private final MusicLibraryStore library;
    private final NeteaseMusicApi api;
    private final MusicDownload downloads;
    private final LyricsManager lyrics;
    private final Map<Long, Integer> progress = new ConcurrentHashMap<>();
    private final Set<Long> coversLoading = ConcurrentHashMap.newKeySet();
    private final Set<String> favoritesLoading = ConcurrentHashMap.newKeySet();

    public MusicService(MusicManager manager, MusicLibraryStore library) {
        this.manager = manager;
        this.library = library;
        api = new NeteaseMusicApi(URI.create(LoginCommand.getApiBase()));
        lyrics = new LyricsManager(api::lyrics, FileLocation.CACHE_DIR.toPath(), Multithreading::runAsync);
        downloads = new MusicDownload(api, library, FileLocation.MUSIC_DIR.toPath(), FileLocation.CACHE_DIR.toPath());
    }

    public LyricsManager lyrics() { return lyrics; }

    public void search(String keyword, int offset, Consumer<NeteaseMusicApi.SearchResult> success,
            Consumer<MusicError> failure) {
        task(() -> api.search(keyword, 30, offset), success, failure);
    }

    public void search(String keyword, int limit, int offset, Consumer<NeteaseMusicApi.SearchResult> success,
            Consumer<MusicError> failure) {
        task(() -> api.search(keyword, limit, offset), success, failure);
    }

    public void download(long id, String quality, boolean play, Consumer<Music> success, Consumer<MusicError> failure) {
        task(() -> {
            List<MusicTrack> found = api.details(List.of(id));
            if (found.isEmpty()) throw new MusicError("music.error.metadata");
            return found.getFirst();
        }, track -> download(track, quality, play, success, failure), failure);
    }

    public void download(MusicTrack track, String quality, boolean play, Consumer<Music> success,
            Consumer<MusicError> failure) {
        if (progress.putIfAbsent(track.id(), 0) != null) {
            failure.accept(new MusicError("music.error.busy"));
            return;
        }
        Account account = account();
        Music playing = manager.getCurrentMusic();
        Path playingFile = playing == null ? null : playing.getAudio().toPath().toAbsolutePath().normalize();
        task(() -> {
            try {
                MusicDownload.Result result = downloads.download(track, quality, account.cookie(), playingFile,
                    value -> progress.put(track.id(), value));
                manager.load();
                return manager.getMusics().stream().filter(m -> m.getAudio().toPath().toAbsolutePath().normalize()
                    .equals(result.audio())).findFirst().orElseThrow(() -> new MusicError("music.error.file"));
            } finally { progress.remove(track.id()); }
        }, music -> {
            if (play) manager.play(music);
            success.accept(music);
        }, failure);
    }

    public int downloadProgress(long id) { return progress.getOrDefault(id, -1); }

    public Music local(MusicTrack track) {
        return manager.getMusics().stream().filter(m -> m.getTrack().id() == track.id() && track.id() > 0)
            .findFirst().orElse(null);
    }

    public File cover(MusicTrack track) {
        Path cover = downloads.cover(track.id());
        if (java.nio.file.Files.isRegularFile(cover)) return cover.toFile();
        if (!track.coverUrl().isBlank() && coversLoading.add(track.id())) {
            // Keep failures in the set until explicit refresh, avoiding a network retry on every frame.
            Multithreading.runAsync(() -> downloads.fetchCover(track));
        }
        return null;
    }

    public void refresh(Consumer<Integer> success, Consumer<MusicError> failure) {
        coversLoading.clear();
        lyrics.clearCache();
        task(() -> {
            manager.load();
            List<Music> legacy = manager.getMusics().stream().filter(m -> m.getTrack().id() == 0
                && LEGACY.matcher(m.getAudio().getName()).matches()).toList();
            if (legacy.isEmpty()) return 0;
            List<Long> ids = legacy.stream().map(m -> {
                Matcher match = LEGACY.matcher(m.getAudio().getName());
                match.matches();
                return Long.parseLong(match.group(1));
            }).toList();
            List<MusicTrack> tracks = api.details(ids);
            for (MusicTrack track : tracks) {
                Music playing = manager.getCurrentMusic();
                downloads.download(track, "exhigh", null, playing == null ? null
                    : playing.getAudio().toPath().toAbsolutePath().normalize(), _ -> { });
            }
            manager.load();
            return tracks.size();
        }, success, failure);
    }

    public boolean loggedIn() { return !account().owner().equals("guest"); }

    public boolean isLiked(MusicTrack track, String filename) {
        return library.isLiked(account().owner(), track, filename);
    }

    public List<MusicLibraryStore.Favorite> favorites() { return library.favorites(account().owner()); }

    public boolean favoritesBusy() { return favoritesLoading.contains(account().owner()); }

    public void toggleLike(MusicTrack track, String filename, Consumer<Boolean> success, Consumer<MusicError> failure) {
        Account account = account();
        if (!favoritesLoading.add(account.owner())) { failure.accept(new MusicError("music.error.busy")); return; }
        boolean liked = !library.isLiked(account.owner(), track, filename);
        task(() -> {
            try {
                if (track.id() > 0 && !account.owner().equals("guest")) api.like(track.id(), liked, account.cookie());
                library.setLiked(account.owner(), track, filename, liked);
                return liked;
            } finally { favoritesLoading.remove(account.owner()); }
        }, success, failure);
    }

    public void syncLikes(Consumer<Integer> success, Consumer<MusicError> failure) {
        Account account = account();
        if (account.owner().equals("guest")) { failure.accept(new MusicError("music.error.login")); return; }
        if (!favoritesLoading.add(account.owner())) { failure.accept(new MusicError("music.error.busy")); return; }
        task(() -> {
            try {
                List<MusicTrack> tracks = api.details(api.likes(account.userId(), account.cookie()));
                library.replaceCloudLikes(account.owner(), tracks);
                return tracks.size();
            } finally { favoritesLoading.remove(account.owner()); }
        }, success, failure);
    }

    private Account account() {
        String cookie = LoginCommand.getCurrentCookie();
        String userId = LoginCommand.getCurrentUserId();
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
