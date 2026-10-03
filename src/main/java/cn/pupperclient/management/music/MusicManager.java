package cn.pupperclient.management.music;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import cn.pupperclient.utils.thread.Multithreading;
import cn.pupperclient.management.music.media.MediaSession;
import cn.pupperclient.management.music.media.WindowsSmtc;

import javax.imageio.ImageIO;

import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;
import cn.pupperclient.libraries.flac.FLACDecoder;
import cn.pupperclient.libraries.flac.metadata.Metadata;
import cn.pupperclient.libraries.flac.metadata.Picture;
import cn.pupperclient.libraries.flac.metadata.VorbisComment;
import cn.pupperclient.utils.render.ImageUtils;
import cn.pupperclient.utils.file.FileLocation;
import cn.pupperclient.utils.file.FileUtils;

public class MusicManager {
    private static final AtomicReferenceFieldUpdater<MusicManager, Music> CURRENT =
        AtomicReferenceFieldUpdater.newUpdater(MusicManager.class, Music.class, "currentMusic");

    private volatile List<Music> musics = List.of();
    private volatile boolean isLoading = false;
    private volatile Music currentMusic;
    private MusicPlayer musicPlayer;
    private volatile boolean shuffle;
    private volatile MusicRepeatMode repeatMode;
    private final MusicLibraryStore library;
    private final MusicService service;
    private final Thread playerThread;
    private final MediaSession mediaSession;
    private volatile boolean shuttingDown;
    private final MusicQueue queue = new MusicQueue();
    private boolean queueLoading;
    private final java.util.Map<String, String> playbackQualities = new java.util.HashMap<>();

    public MusicManager() {

        try {
            library = new MusicLibraryStore(FileLocation.MUSIC_DIR.toPath());
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Could not open the music library", failure);
        }
        service = new MusicService(this, library);

        try {
            load();
        } catch (Exception e) {
            cn.pupperclient.PupperLogger.error("MusicManager", "Failed to load music", e);
        }

        this.musicPlayer = new MusicPlayer(() -> {
            long completedSession = musicPlayer.getGeneration();
            long completedQueue = queue.snapshot().generation();
            Multithreading.runMainThread(() -> {
                if (musicPlayer.getGeneration() != completedSession || !queue.current(completedQueue)
                    || musicPlayer.isPlaying() || queueLoading || shuttingDown) return;
                if (repeatMode == MusicRepeatMode.ONE) { play(); return; }
                if (queue.advance(shuffle, repeatMode == MusicRepeatMode.ALL) != null) prepareQueued(null, _ -> { }, this::queueError);
                else { setCurrentMusic(null); musicPlayer.setPlaying(false); }
            });
        });
        this.shuffle = false;
        this.repeatMode = library.repeatMode();
        musicPlayer.setRepeat(repeatMode == MusicRepeatMode.ONE);
        playerThread = Thread.ofPlatform().daemon().name("Pupper Client music").start(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    musicPlayer.run();
                }
        });
        long window = MediaSession.supported() ? org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(
            net.minecraft.client.Minecraft.getInstance().getWindow().handle()) : 0;
        mediaSession = new MediaSession(window, this::mediaSnapshot, button -> Multithreading.runMainThread(() -> {
            if (shuttingDown) return;
            switch (button) {
                case 0 -> { if (!isPlaying()) switchPlayBack(); }
                case 1, 2 -> stop();
                case 6 -> next();
                case 7 -> back();
                default -> { }
            }
        }));
    }

    public synchronized void load() throws Exception {
        if (isLoading) {
            return; // if is loading return
        }

        synchronized (this) {
            if (isLoading) {
                return;
            }
            isLoading = true;
        }

        try {
            List<Music> loaded = new ArrayList<>();

            File musicDir = FileLocation.MUSIC_DIR;

            File[] files = musicDir.listFiles();
            if (files == null) {
                return;
            }
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File f : files) {
                String name = f.getName().toLowerCase(Locale.ROOT);
                try {
                    if (name.endsWith(".flac")) loaded.add(loadFlacFile(f));
                    else if (name.endsWith(".mp3")) loaded.add(loadMp3File(f));
                } catch (Exception invalid) {
                    cn.pupperclient.PupperLogger.error("MusicManager", "Could not load " + f.getName(), invalid);
                }
            }
            // Publish one complete snapshot; refreshes must not briefly empty a playing library.
            musics = List.copyOf(loaded);
            Music current = currentMusic;
            if (current != null) CURRENT.compareAndSet(this, current,
                loaded.stream().filter(m -> m.getAudio().equals(current.getAudio())).findFirst().orElse(current));
        } finally {
            isLoading = false;
        }
    }

    private Music loadFlacFile(File f) throws Exception {
        Metadata[] metadata;
        try (FileInputStream input = new FileInputStream(f)) {
            metadata = new FLACDecoder(input).readMetadata();
        }
        String title = null;
        String artist = null;
        byte[] imageData = null;

        for (Metadata meta : metadata) {
            if (meta instanceof VorbisComment) {
                VorbisComment comment = (VorbisComment) meta;
                String[] titles = comment.getCommentByName("TITLE");
                if (titles!=null && titles.length > 0) {
                    title = titles[0];
                } else {
                    String fileName = f.getName();
                    title= fileName.substring(0, fileName.lastIndexOf("."));
                }
                String[] artists = comment.getCommentByName("ARTIST");
                if (artists!=null && artists.length > 0) {
                    artist = artists[0];
                }
            } else if (meta instanceof Picture) {
                Picture picture = (Picture) meta;
                imageData = picture.getImage();
            }
        }

        return createMusic(f, title, artist, imageData);
    }

    private Music createMusic(File f, String title, String artist, byte[] imageData) throws Exception {
        MusicTrack metadata = library.metadata(f.getName());
        if (metadata != null) {
            title = metadata.title();
            artist = metadata.artist();
        }
        String fileHash = imageData == null ? "unused" : FileUtils.getMd5Checksum(f);
        File album = new File(FileLocation.CACHE_DIR, fileHash);
        Color color = Color.BLACK;

        if (imageData != null && !album.exists()) {
            FileOutputStream fos = new FileOutputStream(album);
            fos.write(imageData);
            fos.close();
        }

        if (imageData != null && album.exists()) {
            color = ImageUtils.calculateAverageColor(ImageIO.read(album));
        }

        File providerCover = metadata == null ? null : new File(FileLocation.CACHE_DIR, metadata.coverFilename());
        if (providerCover != null && providerCover.isFile()) album = providerCover;
        String fallback = f.getName().substring(0, f.getName().lastIndexOf('.'));
        return new Music(f, title == null || title.isBlank() ? fallback : title,
            artist == null ? "" : artist, album.exists() ? album : null, color, metadata);
    }

    private Music loadMp3File(File f) throws Exception {
        String title = null;
        String artist = null;
        byte[] imageData = null;

        try {
            Mp3File mp3file = new Mp3File(f);
            if (mp3file.hasId3v2Tag()) {
                ID3v2 id3v2Tag = mp3file.getId3v2Tag();
                title = id3v2Tag.getTitle();
                artist = id3v2Tag.getArtist();
                imageData = id3v2Tag.getAlbumImage();
            } else if (mp3file.hasId3v1Tag()) {
                ID3v1 id3v1Tag = mp3file.getId3v1Tag();
                title = id3v1Tag.getTitle();
                artist = id3v1Tag.getArtist();
            }
        } catch (Exception e) {
            cn.pupperclient.PupperLogger.error("MusicManager", "Failed to load MP3 tags", e);
        }

        return createMusic(f, title, artist, imageData);
    }

    public MusicService getService() { return service; }

    public void play(Music music) {
        if (music == null) return;
        List<MusicQueue.Entry> entries = musics.stream().map(MusicQueue.Entry::of).toList();
        int index = -1;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).key().equals(MusicQueue.Entry.of(music).key())) { index = i; break; }
        queue.start(index < 0 ? List.of(MusicQueue.Entry.of(music)) : entries, Math.max(0, index));
        queueLoading = false;
        setCurrentMusic(music);
        play();
    }

    public MusicQueue getQueue() { return queue; }
    public MusicLibraryStore getLibrary() { return library; }
    public boolean isQueueLoading() { return queueLoading; }
    public void playFrom(List<MusicQueue.Entry> entries, int index, String quality,
            java.util.function.Consumer<Music> ready, java.util.function.Consumer<MusicError> failure) {
        queue.start(entries, index);
        if (quality != null) playbackQualities.put(entries.get(index).track().provider(), quality);
        prepareQueued(quality, ready, failure);
    }
    private void prepareQueued(String quality, java.util.function.Consumer<Music> ready,
            java.util.function.Consumer<MusicError> failure) {
        var snapshot = queue.snapshot();
        var entry = snapshot.current();
        if (entry == null || shuttingDown) return;
        queueLoading = true;
        musicPlayer.setPlaying(false);
        Music local = musics.stream().filter(m -> entry.track().remote() ? m.getTrack().sameSong(entry.track())
            : m.getAudio().getName().equals(entry.filename())).findFirst().orElse(null);
        java.util.function.Consumer<Music> complete = music -> {
            if (!queue.current(snapshot.generation()) || shuttingDown) return;
            queueLoading = false; setCurrentMusic(music); play(); ready.accept(music);
        };
        if (local != null) complete.accept(local);
        else if (!entry.track().remote()) { queueLoading = false; failure.accept(new MusicError("music.error.file")); }
        else service.prepare(entry.track(), quality == null ? playbackQualities.getOrDefault(entry.track().provider(),
            service.defaultQuality(entry.track())) : quality, complete, error -> {
            if (!queue.current(snapshot.generation()) || shuttingDown) return;
            queueLoading = false; failure.accept(error);
        });
    }
    public void jumpQueue(int index, long revision) {
        if (queue.jump(index, revision) != null) prepareQueued(null, _ -> { }, this::queueError);
    }
    private void queueError(MusicError error) { cn.pupperclient.utils.chat.ChatUtils.error(MusicText.get(error.key())); }

    public void shutdown() {
        shuttingDown = true;
        service.login().close();
        mediaSession.close();
        playerThread.interrupt();
        musicPlayer.shutdown();
    }

    public void play() {

        if (currentMusic == null) {
            return;
        }

        setVolume(getVolume());
        musicPlayer.setCurrentMusic(currentMusic);
        service.lyrics().get(currentMusic);
    }

    private WindowsSmtc.Snapshot mediaSnapshot() {
        Music music = currentMusic;
        if (music == null) return WindowsSmtc.Snapshot.EMPTY;
        MusicTrack track = music.getTrack();
        String artwork = music.getAlbum() != null && music.getAlbum().isFile() ? music.getAlbum().toURI().toString() : "";
        return new WindowsSmtc.Snapshot(music.getAudio().getAbsolutePath(), track.title(), track.artist(), track.album(),
            artwork, isPlaying(), true, queue.canSwitch() || repeatMode == MusicRepeatMode.ALL, getCurrentTime(), getEndTime());
    }

    public float getVolume() {
        return musicPlayer.getVolume();
    }

    public void setVolume(float volume) {
        musicPlayer.setVolume(volume);
    }

    public void next() {
        if (queue.advance(shuffle, repeatMode == MusicRepeatMode.ALL) != null) { prepareQueued(null, _ -> { }, this::queueError); return; }
        stop(); setCurrentMusic(null);
    }

    public void back() {
        if (!queueLoading && currentMusic != null && getCurrentTime() > 3) { play(); return; }
        if (queue.previous() != null) { prepareQueued(null, _ -> { }, this::queueError); return; }
        if (!queueLoading && currentMusic != null) play();
    }

    public void switchPlayBack() {
        var waiting = queue.snapshot().current();
        if (queueLoading) { stop(); return; }
        if (waiting != null && (currentMusic == null || !waiting.key().equals(MusicQueue.Entry.of(currentMusic).key()))) {
            prepareQueued(null, _ -> { }, this::queueError); return;
        }
        if (currentMusic == null) {
            if (!queue.snapshot().upcoming().isEmpty()) { next(); return; }
            List<Music> snapshot = musics;
            if (!snapshot.isEmpty()) play(snapshot.getFirst());
            return;
        }
        if (musicPlayer.isPlaying()) stop(); else musicPlayer.setPlaying(true);
    }

    public void stop() {
        queue.cancelPending(); queueLoading = false;
        musicPlayer.setPlaying(false);
    }

    public boolean isPlaying() {
        return musicPlayer.isPlaying();
    }

    public float getCurrentTime() {
        return musicPlayer.getCurrentTime();
    }

    public float getEndTime() {
        return musicPlayer.getEndTime();
    }

    public List<Music> getMusics() {
        return musics;
    }

    public Music getCurrentMusic() {
        return currentMusic;
    }

    public void setCurrentMusic(Music currentMusic) {
        this.currentMusic = currentMusic;
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public void setShuffle(boolean shuffle) {
        this.shuffle = shuffle;
    }

    public boolean isRepeat() {
        return repeatMode == MusicRepeatMode.ONE;
    }

    public void setRepeat(boolean repeat) {
        setRepeatMode(repeat ? MusicRepeatMode.ONE : MusicRepeatMode.OFF);
    }
    public MusicRepeatMode getRepeatMode() { return repeatMode; }
    public void cycleRepeatMode() { setRepeatMode(repeatMode.next()); }
    public void setRepeatMode(MusicRepeatMode mode) {
        try { library.repeatMode(mode); }
        catch (java.io.IOException failure) { queueError(new MusicError("music.error.file")); return; }
        repeatMode = mode; musicPlayer.setRepeat(mode == MusicRepeatMode.ONE);
    }
}
