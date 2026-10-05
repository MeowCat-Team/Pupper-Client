package cn.pupperclient.management.music;

import cn.pupperclient.utils.file.FileLocation;
import cn.pupperclient.utils.thread.Multithreading;
import cn.pupperclient.management.music.lyric.LyricsManager;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final MusicLoginService login;
    private final MusicCloudLibrary cloud;
    private final MusicDownloadTasks downloadTasks;
    private final MusicProviders providers;
    private final Map<String, MusicDownload> downloads = new ConcurrentHashMap<>();
    private final LyricsManager lyrics;
    private final Map<String, MusicTrack> observedAccess = new ConcurrentHashMap<>();
    private final Set<String> coversLoading = ConcurrentHashMap.newKeySet();
    private final Set<String> favoritesLoading = ConcurrentHashMap.newKeySet();
    private final Set<MusicPreparation.Cancellation> collectionRequests = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private record PlaybackPin(String prefix, AtomicInteger readers) { }
    private final Map<MusicPreparation.Key, PlaybackPin> playbackPins = new ConcurrentHashMap<>();
    private volatile String prefetchPrefix = "";
    private final ExecutorService playbackWorker = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("Pupper Client music fetch-", 0).factory());
    private final MusicPreparation<Music> preparation = new MusicPreparation<>(playbackWorker, Multithreading::runMainThread);

    public MusicService(MusicManager manager, MusicLibraryStore library) {
        this.manager = manager;
        this.library = library;
        netease = new NeteaseMusicApi(new MusicApiConfiguration(FileLocation.MAIN_DIR.toPath().resolve("music-api.json")));
        login = new MusicLoginService(netease,
            new MusicAccountStore(FileLocation.MAIN_DIR.toPath().resolve("login_status.json")),
            Multithreading::runAsync, Multithreading::runMainThread,
            (task, delay) -> Multithreading.schedule(task, delay, java.util.concurrent.TimeUnit.MILLISECONDS));
        providers = new MusicProviders(library, new NeteaseMusicProvider(netease), new AudiusMusicProvider());
        for (MusicProvider provider : providers.all()) downloads.put(provider.id(), new MusicDownload(provider, library,
            FileLocation.MUSIC_DIR.toPath(), FileLocation.CACHE_DIR.toPath()));
        cloud = new MusicCloudLibrary(netease, login, Multithreading::runAsync, Multithreading::runMainThread);
        downloadTasks = new MusicDownloadTasks(playbackWorker, Multithreading::runMainThread,
            (track, quality, credential, progress, cancellation) -> {
                var downloader = downloads.get(track.provider());
                if (downloader == null) throw new MusicError("music.error.provider");
                var result = downloader.download(track, quality, credential, this::playingPath, progress, cancellation);
                manager.load();
                return result;
            }, result -> observedAccess.put(account(result.track().provider()).owner() + ":" + result.track().key(), result.track()));
        login.onSessionChange(() -> Multithreading.runMainThread(() -> {
            observedAccess.clear(); favoritesLoading.clear(); manager.refreshPrefetch();
        }));
        lyrics = new LyricsManager(track -> providers.get(track.provider()).lyrics(track),
            FileLocation.CACHE_DIR.toPath(), Multithreading::runAsync);
    }

    public LyricsManager lyrics() { return lyrics; }
    public MusicLoginService login() { return login; }
    public MusicCloudLibrary cloud() { return cloud; }
    public MusicDownloadTasks downloads() { return downloadTasks; }
    public MusicApiConfiguration apiConfiguration() { return netease.configuration(); }
    public void configureApi(java.net.URI endpoint, boolean trusted) throws IOException {
        endpoint = MusicApiConfiguration.validate(endpoint);
        var current = netease.configuration().snapshot();
        if (endpoint.equals(current.endpoint()) && trusted == current.trustedAccounts()) return;
        downloadTasks.list().forEach(task -> downloadTasks.cancel(task.id()));
        netease.configure(endpoint, trusted);
        manager.refreshPrefetch();
        observedAccess.clear(); cloud.invalidate(); coversLoading.clear();
    }
    public boolean retryDownload(long id) {
        var selected = downloadTasks.list().stream().filter(task -> task.id() == id).findFirst().orElse(null);
        return selected != null && downloadTasks.retry(id, account(selected.track().provider()).cookie());
    }
    public void retryDownloads() {
        downloadTasks.list().stream().filter(task -> task.state() == MusicDownloadTasks.State.FAILED).forEach(task -> retryDownload(task.id()));
    }
    public List<Long> downloadBatch(List<MusicQueue.Entry> entries, String quality) {
        Map<String, MusicTrack> unique = new java.util.LinkedHashMap<>();
        entries.stream().map(MusicQueue.Entry::track).filter(MusicTrack::remote).filter(MusicTrack::downloadable)
            .forEach(track -> unique.putIfAbsent(track.key(), track));
        return unique.values().stream().filter(track -> local(track) == null || local(track).getTrack().preview())
            .map(track -> downloadTasks.submit(track, track.provider().equals(provider().id()) ? quality : defaultQuality(track),
                account(track.provider()).cookie())).toList();
    }
    public List<Music> libraryTracks() { return manager.getMusics(); }
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

    public void search(String keyword, MusicSearchType type, int offset, Consumer<MusicProvider.CatalogResult> success,
            Consumer<MusicError> failure) {
        MusicProvider selected = provider();
        task(() -> selected.search(keyword, type, 30, offset), success, failure);
    }
    public MusicPreparation.Registration collectionTracks(MusicCollection collection, int offset, Consumer<MusicProvider.SearchResult> success,
            Consumer<MusicError> failure) {
        MusicProvider source;
        try { source = providers.get(collection.provider()); }
        catch (MusicError invalid) { failure.accept(invalid); return () -> { }; }
        String cookie = account(source.id()).cookie();
        var cancellation = new MusicPreparation.Cancellation();
        collectionRequests.add(cancellation);
        if (closed) { collectionRequests.remove(cancellation); cancellation.cancel(); return () -> { }; }
        long session = login.sessionVersion(), revision = apiConfiguration().snapshot().revision();
        var task = new java.util.concurrent.FutureTask<Void>(() -> {
            try {
                var result = source.collectionTracks(collection, 50, offset, cookie, cancellation);
                Multithreading.runMainThread(() -> {
                    collectionRequests.remove(cancellation);
                    if (closed || cancellation.isCancelled()) return;
                    if (source.id().equals("audius") || session == login.sessionVersion() && revision == apiConfiguration().snapshot().revision())
                        success.accept(result);
                    else failure.accept(new MusicError("music.api.error.changed"));
                });
            } catch (Exception error) {
                MusicError translated = error instanceof MusicError musicError ? musicError : new MusicError("music.error.network");
                Multithreading.runMainThread(() -> {
                    collectionRequests.remove(cancellation);
                    if (!closed && !cancellation.isCancelled()) failure.accept(translated);
                });
            }
            return null;
        });
        Multithreading.runAsync(task);
        return () -> { collectionRequests.remove(cancellation); cancellation.cancel(); task.cancel(true); };
    }

    public void download(long id, String quality, boolean play, Consumer<Music> success, Consumer<MusicError> failure) {
        resolve("netease:" + id, track -> download(track, quality, play, success, failure), failure);
    }
    public void resolve(String reference, Consumer<MusicTrack> success, Consumer<MusicError> failure) {
        MusicRequest.Target target;
        try { target = MusicRequest.target(providers, reference, null); }
        catch (MusicError invalid) { failure.accept(invalid); return; }
        task(() -> target.provider().track(target.trackId()), success, failure);
    }
    public void request(MusicRequest.Action action, String reference, String quality,
            Consumer<Music> success, Consumer<MusicError> failure) {
        MusicRequest.Target target;
        try { target = MusicRequest.target(providers, reference, quality); }
        catch (MusicError invalid) { failure.accept(invalid); return; }
        task(() -> target.provider().track(target.trackId()), track ->
            request(action, track, target.quality(), success, failure), failure);
    }
    private void request(MusicRequest.Action action, MusicTrack track, String quality,
            Consumer<Music> success, Consumer<MusicError> failure) {
        if (action == MusicRequest.Action.PLAY) play(track, quality, success, failure);
        else download(track, quality, false, success, failure);
    }
    public void quick(String keyword, Consumer<MusicTrack> selected, Consumer<MusicRequest.Result> success,
            Consumer<MusicError> failure) {
        search(keyword, 1, 0, result -> {
            if (result.tracks().isEmpty()) { failure.accept(new MusicError("music.empty.results")); return; }
            MusicTrack track = result.tracks().getFirst();
            selected.accept(track);
            MusicRequest.Action action = MusicRequest.quickAction(track);
            // Search metadata survives detail outages; the provider's own default quality is used.
            request(action, track, defaultQuality(track), music -> success.accept(new MusicRequest.Result(action, music)), failure);
        }, failure);
    }

    public void download(MusicTrack track, String quality, boolean play, Consumer<Music> success, Consumer<MusicError> failure) {
        acquire(track, quality, play, success, failure);
    }
    public void play(MusicTrack track, String quality, Consumer<Music> success, Consumer<MusicError> failure) {
        manager.playFrom(List.of(new MusicQueue.Entry(track, "")), 0, quality, success, failure);
    }
    /** Playback buffers never publish a library entry; saving is an explicit download action. */
    public MusicPreparation.Registration prepare(MusicTrack track, String quality, Consumer<Music> success, Consumer<MusicError> failure) {
        Music local = local(track);
        if (local != null) { success.accept(local); return () -> { }; }
        Account account = account(track.provider());
        var key = playbackKey(track, quality, account);
        playbackPins.compute(key, (_, pin) -> {
            if (pin == null) pin = new PlaybackPin(MusicDownload.playbackPrefix(track, quality), new AtomicInteger());
            pin.readers().incrementAndGet(); return pin;
        });
        var released = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable release = () -> { if (released.compareAndSet(false, true)) releasePlaybackPin(key); };
        try {
            var subscription = preparation.prepare(key, cancellation -> prepareAudio(track, quality, account, cancellation), music -> {
                try {
                    observedAccess.put(account.owner() + ":" + music.getTrack().key(), music.getTrack());
                    success.accept(music);
                } finally { release.run(); }
            }, error -> {
                try { failure.accept(translate(error)); }
                finally { release.run(); }
            });
            return () -> { try { subscription.close(); } finally { release.run(); } };
        } catch (RuntimeException failureToStart) { release.run(); throw failureToStart; }
    }
    /** Only the selected upcoming song is fetched; it remains an ordinary temporary playback cache. */
    public void prefetch(MusicTrack track, String quality) {
        if (local(track) != null) { cancelPrefetch(); return; }
        Account account = account(track.provider());
        prefetchPrefix = MusicDownload.playbackPrefix(track, quality);
        preparation.prefetch(playbackKey(track, quality, account),
            cancellation -> prepareAudio(track, quality, account, cancellation));
    }
    public void cancelPrefetch() { prefetchPrefix = ""; preparation.cancelPrefetch(); }
    public void close() {
        closed = true;
        collectionRequests.forEach(MusicPreparation.Cancellation::cancel); collectionRequests.clear();
        prefetchPrefix = ""; downloadTasks.close(); cloud.close(); preparation.close(); playbackPins.clear(); playbackWorker.shutdownNow();
    }
    private Music prepareAudio(MusicTrack track, String quality, Account account, MusicPreparation.Cancellation cancellation)
            throws Exception {
        MusicDownload download = downloads.get(track.provider());
        if (download == null) throw new MusicError("music.error.provider");
        MusicDownload.Result result = download.playback(track, quality, account.cookie(), this::playingPath,
            this::protectedPlaybackPrefixes, _ -> { }, cancellation);
        return new Music(result.audio().toFile(), result.track().title(), result.track().artist(),
            java.nio.file.Files.isRegularFile(download.cover(track)) ? download.cover(track).toFile() : null,
            Color.BLACK, result.track());
    }
    private Path playingPath() {
        Music music = manager.getCurrentMusic();
        return music == null ? null : music.getAudio().toPath().toAbsolutePath().normalize();
    }
    private Set<String> protectedPlaybackPrefixes() {
        Set<String> prefixes = new java.util.HashSet<>();
        String next = prefetchPrefix; if (!next.isEmpty()) prefixes.add(next);
        playbackPins.values().forEach(pin -> prefixes.add(pin.prefix()));
        return prefixes;
    }
    private void releasePlaybackPin(MusicPreparation.Key key) {
        playbackPins.computeIfPresent(key, (_, pin) -> pin.readers().decrementAndGet() == 0 ? null : pin);
    }
    private static MusicPreparation.Key playbackKey(MusicTrack track, String quality, Account account) {
        try {
            byte[] credential = (account.cookie() == null ? "" : account.cookie()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(credential));
            return new MusicPreparation.Key(track.key(), quality, account.owner() + ":" + fingerprint);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static MusicError translate(Throwable error) {
        return error instanceof MusicError musicError ? musicError
            : new MusicError(error instanceof IOException ? "music.error.file" : "music.error.network");
    }
    private void acquire(MusicTrack track, String quality, boolean play, Consumer<Music> success,
            Consumer<MusicError> failure) {
        if (!downloads.containsKey(track.provider())) { failure.accept(new MusicError("music.error.provider")); return; }
        downloadTasks.submit(track, quality, account(track.provider()).cookie(), result -> {
            Music music = manager.getMusics().stream().filter(item -> item.getAudio().toPath().toAbsolutePath().normalize()
                .equals(result.audio())).findFirst().orElse(null);
            if (music == null) { failure.accept(new MusicError("music.error.file")); return; }
            if (play) manager.play(music);
            success.accept(music);
        }, failure);
    }

    public int downloadProgress(MusicTrack track) { return downloadTasks.progress(track); }
    public Music local(MusicTrack track) {
        return manager.getMusics().stream().filter(m -> m.getTrack().sameSong(track)).findFirst().orElse(null);
    }
    /** Song fees describe the catalogue; preview length describes the audio actually returned for this account. */
    public MusicTrack displayTrack(MusicTrack track) {
        Music local = local(track);
        MusicTrack known = local == null ? observedAccess.get(account(track.provider()).owner() + ":" + track.key()) : local.getTrack();
        return known == null ? track : track.withAccess(known.fee() == 0 ? track.fee() : known.fee(), known.previewMillis());
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
    public File cover(MusicCollection collection) {
        MusicDownload download = downloads.get(collection.provider()); if (download == null) return null;
        Path cover = download.cover(collection);
        if (java.nio.file.Files.isRegularFile(cover)) return cover.toFile();
        if (!collection.coverUrl().isBlank() && coversLoading.add(collection.key()))
            Multithreading.runAsync(() -> download.fetchCover(collection));
        return null;
    }

    public void refresh(Consumer<Integer> success, Consumer<MusicError> failure) {
        coversLoading.clear();
        lyrics.clearCache();
        task(() -> {
            manager.load();
            List<Music> snapshot = manager.getMusics();
            List<Music> legacy = snapshot.stream().filter(m -> !m.getTrack().remote()
                && LEGACY.matcher(m.getAudio().getName()).matches()).toList();
            List<Long> ids = new java.util.ArrayList<>(legacy.stream().map(m -> {
                Matcher match = LEGACY.matcher(m.getAudio().getName()); match.matches();
                return Long.parseLong(match.group(1));
            }).toList());
            snapshot.stream().map(Music::getTrack).filter(track -> track.remote() && track.provider().equals("netease"))
                .map(MusicTrack::id).filter(id -> id > 0).forEach(ids::add);
            if (ids.isEmpty()) return 0;
            List<MusicTrack> tracks;
            try { tracks = netease.details(ids.stream().distinct().toList()); }
            catch (MusicError unavailable) { if (legacy.isEmpty()) return 0; throw unavailable; }
            for (MusicTrack track : tracks) {
                if (!ids.contains(track.id())) continue;
                List<Music> saved = snapshot.stream().filter(m -> m.getTrack().sameSong(track)).toList();
                if (saved.isEmpty()) {
                    Music playing = manager.getCurrentMusic();
                    downloads.get("netease").download(track, "exhigh", null, playing == null ? null
                        : playing.getAudio().toPath().toAbsolutePath().normalize(), _ -> { });
                } else for (Music music : saved) library.refreshMetadata(music.getAudio().getName(), track);
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
        MusicAccount account = login.account();
        return account.authenticated() ? new Account(account.owner(), account.cookie(), account.userId())
            : new Account("guest", null, null);
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
