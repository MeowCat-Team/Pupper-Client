/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cn.pupperclient.management.music;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import cn.pupperclient.utils.thread.Multithreading;
import cn.pupperclient.management.music.media.MediaSession;
import cn.pupperclient.management.music.media.WindowsSmtc;


import cn.pupperclient.utils.file.FileLocation;

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
    private final MusicExperienceStore experience;
    private final MusicService service;
    private final Thread playerThread;
    private final MediaSession mediaSession;
    private volatile boolean shuttingDown;
    private final MusicQueue queue = new MusicQueue();
    private boolean queueLoading;
    private boolean selectionNeedsPreparation;
    private boolean historyWarningSent;
    private MusicPreparation.Registration pendingPreparation;
    private final java.util.Map<String, String> playbackQualities = new java.util.HashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean prefetchUpdateQueued = new java.util.concurrent.atomic.AtomicBoolean();
    private MusicPreparation.Registration transitionPreparation;
    private long transitionEpoch;
    private volatile Handoff handoff;
    private record Handoff(Music from, Music next, long playerGeneration, long queueGeneration, long revision,
            boolean shuffle, boolean repeatAll, java.util.concurrent.atomic.AtomicBoolean claimed) { }

    public MusicManager() {

        try {
            library = new MusicLibraryStore(FileLocation.MUSIC_DIR.toPath());
            experience = new MusicExperienceStore(FileLocation.MUSIC_DIR.toPath());
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Could not open the music library", failure);
        }
        service = new MusicService(this, library);

        // Reading tags, cover art and checksums for a whole library takes long enough to
        // stall client startup, so the first scan runs off-thread and publishes when done.
        Multithreading.runAsync(() -> {
            try {
                load();
            } catch (Exception e) {
                cn.pupperclient.PupperLogger.error("MusicManager", "Failed to load music", e);
            }
        });

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
        var options = experience.settings();
        musicPlayer.setPlaybackOptions(options.gapless(), options.crossfadeSeconds(), options.normalize());
        musicPlayer.setVolume(experience.resume().volume());
        musicPlayer.setRepeat(repeatMode == MusicRepeatMode.ONE);
        playerThread = Thread.ofPlatform().daemon().name("Pupper Client music").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // The player signals every state change; polling at 100 Hz only burned CPU.
                    if (!musicPlayer.awaitWork()) break;
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
        }), seconds -> Multithreading.runMainThread(() -> seek(seconds)));
        queue.setChangeListener(this::schedulePrefetch);
        scheduleRecovery();
    }

    private void scheduleRecovery() {
        Multithreading.schedule(() -> {
            if (shuttingDown) return;
            // Writing the resume point rewrites a JSON file; keep it off the client thread.
            Multithreading.runAsync(() -> {
                if (shuttingDown) return;
                savePlayback();
                scheduleRecovery();
            });
        }, 10, java.util.concurrent.TimeUnit.SECONDS);
    }

    public synchronized void load() throws Exception {
        if (isLoading) {
            return;
        }
        synchronized (this) {
            if (isLoading) {
                return;
            }
            isLoading = true;
        }

        try {
            // Publish one complete snapshot; refreshes must not briefly empty a playing library.
            List<Music> loaded = MusicLibraryScanner.scan(library);
            musics = loaded;
            Music current = currentMusic;
            if (current != null) {
                CURRENT.compareAndSet(this, current, loaded.stream()
                    .filter(m -> m.getAudio().equals(current.getAudio())).findFirst().orElse(current));
            }
        } finally {
            isLoading = false;
        }
    }

    public MusicService getService() { return service; }

    public void play(Music music) {
        if (music == null) return;
        clearTransition();
        cancelQueuedPreparation();
        List<MusicQueue.Entry> entries = musics.stream().map(MusicQueue.Entry::of).toList();
        int index = -1;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).key().equals(MusicQueue.Entry.of(music).key())) { index = i; break; }
        queue.start(index < 0 ? List.of(MusicQueue.Entry.of(music)) : entries, Math.max(0, index));
        queueLoading = false; selectionNeedsPreparation = false;
        setCurrentMusic(music);
        play();
    }

    public MusicQueue getQueue() { return queue; }
    public MusicLibraryStore getLibrary() { return library; }
    public MusicExperienceStore experience() { return experience; }
    public void settings(MusicExperienceStore.Settings options) throws java.io.IOException {
        boolean refreshLeveling = experience.settings().normalize() != options.normalize();
        experience.settings(options);
        clearTransition();
        musicPlayer.setPlaybackOptions(options.gapless(), options.crossfadeSeconds(), options.normalize());
        // A new decoder recomputes the bounded gain sample while keeping the requested time and pause state.
        if (refreshLeveling && getCurrentMusic() != null && !queueLoading && !selectionNeedsPreparation)
            seek(getCurrentTime());
        schedulePrefetch();
    }
    public boolean seek(double seconds) {
        if (queueLoading || selectionNeedsPreparation || shuttingDown || getCurrentMusic() == null || !Double.isFinite(seconds)) return false;
        clearTransition();
        boolean accepted = musicPlayer.seek(seconds);
        if (accepted) { savePlayback(); schedulePrefetch(); }
        return accepted;
    }
    public boolean restorePlayback() {
        var saved = experience.resume();
        if (!saved.available()) return false;
        setShuffle(saved.shuffle()); setVolume(saved.volume());
        playFrom(saved.entries(), 0, null, _ -> seek(saved.seconds()), this::queueError);
        return true;
    }
    public boolean isQueueLoading() { return queueLoading; }
    public void playFrom(List<MusicQueue.Entry> entries, int index, String quality,
            java.util.function.Consumer<Music> ready, java.util.function.Consumer<MusicError> failure) {
        clearTransition();
        queue.start(entries, index);
        if (quality != null) playbackQualities.put(entries.get(index).track().provider(), quality);
        prepareQueued(quality, ready, failure);
    }
    private void prepareQueued(String quality, java.util.function.Consumer<Music> ready,
            java.util.function.Consumer<MusicError> failure) {
        clearTransition();
        var snapshot = queue.snapshot();
        var entry = snapshot.current();
        if (entry == null || shuttingDown) return;
        var previousPreparation = pendingPreparation;
        pendingPreparation = null;
        selectionNeedsPreparation = true;
        queueLoading = true;
        musicPlayer.setPlaying(false);
        Music local = musics.stream().filter(m -> entry.track().remote() ? m.getTrack().sameSong(entry.track())
            : m.getAudio().getName().equals(entry.filename())).findFirst().orElse(null);
        java.util.function.Consumer<Music> complete = music -> {
            if (!queue.current(snapshot.generation()) || shuttingDown) return;
            pendingPreparation = null;
            queueLoading = false; selectionNeedsPreparation = false;
            setCurrentMusic(music); play(); ready.accept(music);
        };
        try {
            if (local != null) complete.accept(local);
            else if (!entry.track().remote()) { queueLoading = false; failure.accept(new MusicError("music.error.file")); }
            else {
                var subscription = service.prepare(entry.track(), quality == null ? playbackQualities.getOrDefault(entry.track().provider(),
                    service.defaultQuality(entry.track())) : quality, complete, error -> {
                    if (!queue.current(snapshot.generation()) || shuttingDown) return;
                    pendingPreparation = null; queueLoading = false; failure.accept(error);
                });
                if (queueLoading && queue.current(snapshot.generation())) pendingPreparation = subscription;
                else subscription.close();
            }
        } finally {
            // Join the new selection first: identical queue occurrences can share the existing transfer.
            if (previousPreparation != null) previousPreparation.close();
        }
    }
    private void cancelQueuedPreparation() {
        var subscription = pendingPreparation; pendingPreparation = null;
        if (subscription != null) subscription.close();
    }
    public void jumpQueue(int index, long revision) {
        clearTransition();
        if (queue.jump(index, revision) != null) prepareQueued(null, _ -> { }, this::queueError);
    }
    private void queueError(MusicError error) { cn.pupperclient.utils.chat.ChatUtils.error(MusicText.get(error.key())); }

    private void schedulePrefetch() {
        if (shuttingDown || !prefetchUpdateQueued.compareAndSet(false, true)) return;
        // Defer even render-thread edits: advance must be able to adopt an active fetch first.
        Multithreading.runAsync(() -> Multithreading.runMainThread(() -> {
            prefetchUpdateQueued.set(false);
            prefetchNext();
        }));
    }
    /** A new account must not adopt a speculative request authenticated by the previous session. */
    public void refreshPrefetch() { clearTransition(); service.cancelPrefetch(); schedulePrefetch(); }
    private void prefetchNext() {
        reconcileHandoff();
        var snapshot = queue.snapshot();
        Music current = currentMusic;
        if (shuttingDown || queueLoading || selectionNeedsPreparation || !isPlaying() || repeatMode == MusicRepeatMode.ONE
                || current == null || snapshot.current() == null
                || !snapshot.current().key().equals(MusicQueue.Entry.of(current).key())) {
            clearTransition(); service.cancelPrefetch(); return;
        }
        var next = queue.peekNext(shuffle, repeatMode == MusicRepeatMode.ALL);
        if (next == null || !next.playable()) {
            clearTransition(); service.cancelPrefetch(); return;
        }
        String quality = playbackQualities.getOrDefault(next.track().provider(), service.defaultQuality(next.track()));
        if (next.track().remote() && service.local(next.track()) == null) service.prefetch(next.track(), quality);
        else service.cancelPrefetch();
        var options = experience.settings();
        if (!options.gapless() && options.crossfadeSeconds() == 0) { clearTransition(); return; }
        Handoff previous = handoff;
        if (previous != null && previous.queueGeneration() == snapshot.generation() && previous.revision() == snapshot.revision()
                && MusicQueue.Entry.of(previous.next()).key().equals(next.key())) return;
        clearTransition();
        long epoch = transitionEpoch;
        java.util.function.Consumer<Music> prepared = music -> {
            var actual = queue.snapshot();
            if (epoch != transitionEpoch || shuttingDown || actual.generation() != snapshot.generation()
                    || actual.revision() != snapshot.revision() || !isPlaying()) return;
            Handoff plan = new Handoff(current, music, musicPlayer.getGeneration(), snapshot.generation(), snapshot.revision(),
                shuffle, repeatMode == MusicRepeatMode.ALL, new java.util.concurrent.atomic.AtomicBoolean());
            handoff = plan;
            musicPlayer.prepareNext(music, () -> {
                if (shuttingDown || handoff != plan) return false;
                boolean claimed = queue.advancePrepared(plan.queueGeneration(), plan.revision(), next.key(), plan.shuffle(), plan.repeatAll()) != null;
                if (claimed) plan.claimed().set(true);
                return claimed;
            }, () -> Multithreading.runMainThread(this::reconcileHandoff));
        };
        Music local = next.track().remote() ? service.local(next.track()) : musics.stream()
            .filter(music -> music.getAudio().getName().equals(next.filename())).findFirst().orElse(null);
        if (local != null) prepared.accept(local);
        else if (next.track().remote()) transitionPreparation = service.prepare(next.track(), quality, prepared, _ -> { });
    }
    private void reconcileHandoff() {
        Handoff plan = handoff;
        if (plan == null || !plan.claimed().get() || musicPlayer.getCurrentMusic() != plan.next()
                || musicPlayer.getGeneration() == plan.playerGeneration()) return;
        currentMusic = plan.next(); handoff = null; queueLoading = false; selectionNeedsPreparation = false;
        recordPlayed(); service.lyrics().get(currentMusic); savePlayback(); schedulePrefetch();
    }
    private void clearTransition() {
        if (musicPlayer == null) return;
        reconcileHandoff(); transitionEpoch++;
        musicPlayer.clearNext();
        // The player-lock barrier reconciles an adoption that won before cancellation.
        reconcileHandoff(); handoff = null;
        var subscription = transitionPreparation; transitionPreparation = null;
        if (subscription != null) subscription.close();
    }
    private void recordPlayed() {
        Music music = getCurrentMusic(); if (music == null) return;
        try { experience.played(MusicQueue.Entry.of(music)); }
        catch (java.io.IOException failure) { historyFailure(failure); }
    }
    private void savePlayback() {
        if (musicPlayer == null) return;
        var snapshot = queue.snapshot(); Music playing = getCurrentMusic();
        if (snapshot.current() == null || playing == null || !snapshot.current().key().equals(MusicQueue.Entry.of(playing).key())) return;
        var entries = new ArrayList<MusicQueue.Entry>(); entries.add(snapshot.current()); entries.addAll(snapshot.upcoming());
        try { experience.resume(new MusicExperienceStore.Resume(entries, getCurrentTime(), shuffle, getVolume())); }
        catch (java.io.IOException failure) { historyFailure(failure); }
    }

    private void historyFailure(java.io.IOException failure) {
        if (historyWarningSent) return;
        historyWarningSent = true;
        cn.pupperclient.PupperClient.LOGGER.warn("Could not save listening history; audio playback is unaffected", failure);
        if (!shuttingDown) cn.pupperclient.utils.chat.ChatUtils.addChatMessage("§e" + MusicText.get("music.warning.history"));
    }

    public void shutdown() {
        savePlayback(); clearTransition();
        shuttingDown = true;
        queue.setChangeListener(null);
        cancelQueuedPreparation();
        service.close();
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
        recordPlayed(); savePlayback();
        service.lyrics().get(currentMusic);
        schedulePrefetch();
    }

    private WindowsSmtc.Snapshot mediaSnapshot() {
        Music music = getCurrentMusic();
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
        clearTransition();
        if (queue.advance(shuffle, repeatMode == MusicRepeatMode.ALL) != null) { prepareQueued(null, _ -> { }, this::queueError); return; }
        stop(); setCurrentMusic(null);
    }

    public void back() {
        clearTransition();
        if (!queueLoading && !selectionNeedsPreparation && currentMusic != null && getCurrentTime() > 3) { play(); return; }
        if (queue.previous() != null) { prepareQueued(null, _ -> { }, this::queueError); return; }
        if (!queueLoading && selectionNeedsPreparation) prepareQueued(null, _ -> { }, this::queueError);
        else if (!queueLoading && currentMusic != null) play();
    }

    public void switchPlayBack() {
        var waiting = queue.snapshot().current();
        if (queueLoading) { stop(); return; }
        if (waiting != null && (selectionNeedsPreparation || currentMusic == null || !waiting.key().equals(MusicQueue.Entry.of(currentMusic).key()))) {
            prepareQueued(null, _ -> { }, this::queueError); return;
        }
        if (currentMusic == null) {
            if (!queue.snapshot().upcoming().isEmpty()) { next(); return; }
            List<Music> snapshot = musics;
            if (!snapshot.isEmpty()) play(snapshot.getFirst());
            return;
        }
        if (musicPlayer.isPlaying()) stop(); else { musicPlayer.setPlaying(true); schedulePrefetch(); }
    }

    public void stop() {
        clearTransition(); savePlayback();
        queue.cancelPending(); queueLoading = false;
        cancelQueuedPreparation();
        musicPlayer.setPlaying(false);
        service.cancelPrefetch();
    }

    public boolean isPlaying() {
        return musicPlayer.isPlaying();
    }

    public float getCurrentTime() {
        return musicPlayer.getCurrentTime();
    }
    public long getPlaybackGeneration() { return musicPlayer.getGeneration(); }

    public float getEndTime() {
        return musicPlayer.getEndTime();
    }

    public List<Music> getMusics() {
        return musics;
    }

    public Music getCurrentMusic() {
        Handoff plan = handoff;
        if (plan != null && musicPlayer != null && plan.claimed().get() && musicPlayer.getCurrentMusic() == plan.next()
                && musicPlayer.getGeneration() != plan.playerGeneration()) return plan.next();
        return currentMusic;
    }

    public void setCurrentMusic(Music currentMusic) {
        this.currentMusic = currentMusic;
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public void setShuffle(boolean shuffle) {
        clearTransition();
        this.shuffle = shuffle;
        schedulePrefetch();
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
        clearTransition(); repeatMode = mode; musicPlayer.setRepeat(mode == MusicRepeatMode.ONE);
        schedulePrefetch();
    }
}
