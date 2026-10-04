package cn.pupperclient.music;

import cn.pupperclient.management.music.Music;
import cn.pupperclient.management.music.MusicPlayer;
import java.awt.Color;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

/** Real MP3/FLAC decoders, generated silence and an instrumented line; never opens audio hardware. */
final class MusicPlaybackChecks {
    private static int checks;

    static void run() throws Exception {
        Path root = Files.createTempDirectory("pupper-playback-checks-");
        try {
            Music mp3 = fixture(root, "mp3"), flac = fixture(root, "flac");
            AtomicInteger completions = new AtomicInteger();
            TestPlayer player = new TestPlayer(completions);
            Thread worker = worker(player);
            try {
                player.setVolume(0);
                player.setCurrentMusic(mp3);
                await(() -> !player.lines.isEmpty() && player.lines.getFirst().writes.get() >= 3);
                SpyLine mp3Line = player.lines.getFirst();
                require(mp3Line.gain.getValue() == mp3Line.gain.getMinimum(), "Zero volume is not clamped");
                player.setPlaying(false);
                Thread.sleep(50); // Allow one frame already in flight to finish.
                int pausedAt = mp3Line.writes.get();
                Thread.sleep(70);
                require(mp3Line.writes.get() == pausedAt && completions.get() == 0, "Paused MP3 still advances or completes");
                player.setPlaying(true);
                await(() -> completions.get() == 1 && mp3Line.closed);
                require(mp3Line.bytes > 100_000 && !player.isPlaying(), "MP3 did not decode and finish");
                require(player.getEndTime() >= 2, "MP3 duration missing");
                Thread.sleep(30);
                require(completions.get() == 1, "Completed MP3 restarts on worker tick");

                player.setCurrentMusic(flac);
                await(() -> completions.get() == 2 && player.lines.size() == 2 && player.lines.getLast().closed);
                SpyLine flacLine = player.lines.getLast();
                require(flacLine.bytes == 176_400, "FLAC decoder lost PCM samples");
                require(player.getEndTime() == 2 && flacLine.format.getSampleRate() == 44_100,
                    "FLAC duration/format missing");

                player.setCurrentMusic(mp3);
                await(() -> player.lines.size() == 3 && player.lines.getLast().writes.get() >= 3);
                SpyLine abandoned = player.lines.getLast();
                player.setPlaying(false);
                Thread.sleep(40);
                player.setCurrentMusic(flac);
                await(() -> completions.get() == 3 && player.lines.size() == 4 && player.lines.getLast().closed);
                require(abandoned.closed && abandoned.bytes < mp3Line.bytes,
                    "Switching a paused track did not close the old decoder session");
                require(completions.get() == 3, "Abandoned track emitted a completion callback");

                player.setCurrentMusic(mp3);
                await(() -> player.lines.size() == 5 && player.lines.getLast().writes.get() >= 2);
                player.setPlaying(false);
                player.shutdown();
                worker.interrupt();
                worker.join(2_000);
                require(!worker.isAlive() && player.lines.getLast().closed, "Shutdown left a paused worker or audio line open");
                require(completions.get() == 3, "Shutdown emitted a completion callback");
            } finally {
                player.shutdown();
                worker.interrupt();
                worker.join(2_000);
            }
            checkRepeat(mp3);
            checkRepeat(flac);
            checkOutputCleanupRace(mp3, false);
            checkOutputCleanupRace(flac, true);
            System.out.println("Music playback checks passed: " + checks
                + " assertions; real MP3/FLAC decoding, repeat on/off, pause/resume, switch/cleanup race, mute and shutdown without audio hardware.");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static Music fixture(Path root, String extension) throws Exception {
        Path path = root.resolve("silence." + extension);
        try (var source = MusicPlaybackChecks.class.getResourceAsStream("/music/silence." + extension)) {
            if (source == null) throw new AssertionError("Missing generated silence fixture");
            Files.copy(source, path);
        }
        return new Music(path.toFile(), "Generated silence", "", null, Color.BLACK);
    }

    private static void checkRepeat(Music track) throws Exception {
        AtomicInteger completions = new AtomicInteger();
        TestPlayer player = new TestPlayer(completions);
        player.setRepeat(true);
        player.setCurrentMusic(track);
        Thread worker = worker(player);
        try {
            await(() -> player.lines.size() >= 3 && player.lines.get(0).closed && player.lines.get(1).closed);
            require(completions.get() == 0 && player.isPlaying(), "Repeat depended on the UI completion callback");
            require(player.lines.get(0).bytes == player.lines.get(1).bytes, "Repeated decoder lost or duplicated PCM");
            require(player.getGeneration() >= 3, "Repeat did not open fresh decoder sessions");
            player.setRepeat(false);
            await(() -> completions.get() == 1 && !player.isPlaying() && player.lines.getLast().closed);
            int count = player.lines.size();
            Thread.sleep(40);
            require(player.lines.size() == count && completions.get() == 1, "Disabling repeat continued looping");
        } finally {
            player.shutdown(); worker.interrupt(); worker.join(2_000);
        }
    }

    private static Thread worker(MusicPlayer player) {
        return Thread.ofPlatform().daemon().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                player.run();
                try { Thread.sleep(2); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        });
    }

    private static void checkOutputCleanupRace(Music track, boolean shutdown) throws Exception {
        TestPlayer player = new TestPlayer(new AtomicInteger());
        var output = MusicPlayer.class.getDeclaredField("sourceDataLine");
        output.setAccessible(true);
        SpyLine old = new SpyLine(new AudioFormat(44_100, 16, 2, true, false));
        output.set(player, old.line);
        // Deterministically reproduce cleanup clearing the volatile field between stop and flush.
        old.onStop = () -> {
            try { output.set(player, null); }
            catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        };
        if (shutdown) player.shutdown(); else player.setCurrentMusic(track);
        require(old.stops == 1 && old.flushes == 1 && old.closed, "Concurrent cleanup interrupted audio output shutdown");
        require(output.get(player) == null && player.isPlaying() == !shutdown, "Closing output left stale state");

        SpyLine newer = new SpyLine(old.format);
        output.set(player, newer.line);
        var release = MusicPlayer.class.getDeclaredMethod("releaseLine", SourceDataLine.class);
        release.setAccessible(true);
        release.invoke(player, old.line);
        require(output.get(player) == newer.line && !newer.closed, "Old decoder cleanup detached a new output");
        player.shutdown();
        player.shutdown();
        require(newer.stops == 1 && newer.flushes == 1 && newer.closed, "Repeated shutdown reused closed output");
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Playback operation timed out");
            Thread.sleep(5);
        }
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class TestPlayer extends MusicPlayer {
        final List<SpyLine> lines = new CopyOnWriteArrayList<>();
        TestPlayer(AtomicInteger completions) { super(completions::incrementAndGet); }
        @Override protected SourceDataLine createLine(AudioFormat format) {
            SpyLine spy = new SpyLine(format);
            lines.add(spy);
            return spy.line;
        }
    }

    private static final class SpyLine {
        final AudioFormat format;
        final AtomicInteger writes = new AtomicInteger();
        final FloatControl gain = new FloatControl(FloatControl.Type.MASTER_GAIN, -80, 6, .1f, 1, 0, "dB") { };
        final SourceDataLine line;
        volatile boolean closed;
        volatile long bytes;
        volatile Runnable onStop = () -> {};
        int stops, flushes;

        SpyLine(AudioFormat format) {
            this.format = format;
            line = (SourceDataLine) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {SourceDataLine.class}, (_, method, args) -> {
                    switch (method.getName()) {
                        case "write" -> {
                            if (closed) throw new IllegalStateException("Closed fixture line");
                            int length = (int) args[2];
                            bytes += length;
                            writes.incrementAndGet();
                            try { Thread.sleep(10); }
                            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                            return length;
                        }
                        case "stop" -> { stops++; onStop.run(); return null; }
                        case "flush" -> { flushes++; return null; }
                        case "close" -> { closed = true; return null; }
                        case "getControl" -> { return gain; }
                        case "getControls" -> { return new javax.sound.sampled.Control[] {gain}; }
                        case "getFormat" -> { return format; }
                        case "isOpen", "isRunning", "isActive", "isControlSupported" -> { return !closed; }
                        case "getMicrosecondPosition" -> { return (long) (bytes * 1_000_000d / format.getFrameSize() / format.getFrameRate()); }
                        case "getFramePosition" -> { return (int) (bytes / format.getFrameSize()); }
                        case "getLongFramePosition" -> { return bytes / format.getFrameSize(); }
                        default -> {
                            if (method.getReturnType() == int.class) return 0;
                            if (method.getReturnType() == float.class) return 0f;
                            return null;
                        }
                    }
                });
        }
    }
}
