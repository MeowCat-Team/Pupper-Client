package cn.pupperclient.music;

import cn.pupperclient.management.music.Music;
import cn.pupperclient.management.music.MusicPlayer;
import cn.pupperclient.management.music.audio.PcmDecoder;
import cn.pupperclient.management.music.audio.PcmDecoders;
import cn.pupperclient.management.music.audio.PcmProcessing;
import cn.pupperclient.management.music.audio.PcmStream;
import cn.pupperclient.management.music.audio.Mp3Gapless;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

/** Actual decoder PCM assertions with instrumented output; no hardware audio or Minecraft initialization. */
public final class MusicAudioChecks {
    private static int checks;
    private MusicAudioChecks() { }
    public static void main(String[] args) throws Exception { MusicPlaybackChecks.run(); run(); }
    public static void run() throws Exception {
        checks = 0;
        Path root = Files.createTempDirectory("pupper-audio-checks-");
        try {
            seekTone(root);
            seekCodec(root, "mp3"); seekCodec(root, "flac");
            encodedTone(root);
            transition(root, false); transition(root, true);
            liveOptions(root, false); liveOptions(root, true);
            rejectAndCancelTransition(root);
            preparedOwnership(root, false); preparedOwnership(root, true);
            normalization(root);
            conversion(root);
            System.out.println("Music audio checks: " + checks + " passed; real sample seek, pause, PCM overlap, one-line gapless, conversion and bounded leveling");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void seekTone(Path root) throws Exception {
        short[] samples = new short[16000];
        for (int f = 0; f < samples.length; f++) samples[f] = (short) ((f % 997 - 498) * 30);
        Music track = wave(root, "seek", 8000, 1, samples);
        Player player = new Player();
        player.setCurrentMusic(track); player.setPlaying(false);
        require(player.seek(1), "Paused seek was rejected");
        Thread worker = worker(player);
        try {
            await(() -> player.lines.size() == 1 && !player.isSeeking());
            Line paused = player.lines.getFirst();
            require(!player.isPlaying() && paused.frames() == 0 && player.getCurrentTime() == 1, "Seek did not preserve pause or decode-discard exact sample offset");
            require(paused.starts.get() == 0, "Paused seek unexpectedly started output");
            player.setPlaying(true);
            await(() -> player.completions.get() == 1 && paused.closed);
            require(Arrays.equals(paused.samples(), Arrays.copyOfRange(samples, 8000, 16000)), "Seek output is not exactly the source PCM after the target sample");
            require(player.getCurrentTime() == 2, "Seek position offset was lost at completion");
            require(!player.seek(Double.NaN) && !player.seek(Double.POSITIVE_INFINITY), "Non-finite seek positions were accepted");
            player.setCurrentMusic(track);
            await(() -> player.lines.size() == 2 && player.lines.getLast().frames() >= 2048);
            Line old = player.lines.getLast();
            player.setPlaying(false); player.seek(.5); player.seek(1.5);
            await(() -> player.lines.size() >= 3 && !player.isSeeking());
            Line replacement = player.lines.getLast();
            require(old.closed && old.flushes.get() > 0, "Seek left the previous output buffer/session open");
            require(!player.isPlaying() && Math.abs(player.getCurrentTime() - 1.5f) < .001, "Last rapid seek did not win while paused");
            player.setPlaying(true);
            await(() -> player.completions.get() == 2 && replacement.closed);
            require(Arrays.equals(replacement.samples(), Arrays.copyOfRange(samples, 12000, 16000)), "Rapid seek played stale or duplicated decoder PCM");
        } finally { stop(player, worker); }
        require(!player.seek(0), "Closed player accepts seek work");
    }

    private static void seekCodec(Path root, String extension) throws Exception {
        Path file = root.resolve("seek-codec." + extension);
        try (var source = MusicAudioChecks.class.getResourceAsStream("/music/silence." + extension)) {
            if (source == null) throw new AssertionError("Missing actual codec fixture");
            Files.copy(source, file);
        }
        Music track = new Music(file.toFile(), extension, "", null, Color.BLACK);
        long decoded = 0;
        float rate;
        int channels;
        try (var decoder = PcmDecoders.open(file.toFile())) {
            rate = decoder.format().getSampleRate(); channels = decoder.format().getChannels();
            short[] block; while ((block = decoder.read()) != null) decoded += block.length / channels;
        }
        Player player = new Player(); player.setCurrentMusic(track); player.setPlaying(false); player.seek(1);
        Thread worker = worker(player);
        try {
            await(() -> !player.isSeeking() && !player.lines.isEmpty());
            require(player.getCurrentTime() == 1 && !player.isPlaying(), extension + " seek must finish decoding while paused");
            player.setPlaying(true);
            await(() -> player.completions.get() == 1 && player.lines.getFirst().closed);
            require(player.lines.getFirst().frames() == decoded - (long) rate, extension + " seek did not discard exactly one second of real decoded PCM");
        } finally { stop(player, worker); }
    }

    /** Fixtures encode one second of 0.25*sin(2*pi*(200*t+250*t*t)) at 44.1 kHz with LAME and FLAC. */
    private static void encodedTone(Path root) throws Exception {
        Music[] tracks = new Music[2]; short[][] pcm = new short[2][];
        for (int i = 0; i < 2; i++) {
            String extension = i == 0 ? "mp3" : "flac";
            Path file = root.resolve("encoded-tone." + extension);
            try (var source = MusicAudioChecks.class.getResourceAsStream("/music/tone." + extension)) {
                if (source == null) throw new AssertionError("Missing generated chirp fixture");
                Files.copy(source, file);
            }
            tracks[i] = new Music(file.toFile(), "Encoded chirp " + extension, "Generated fixture", null, Color.BLACK);
            try (var decoder = PcmDecoders.open(file.toFile())) {
                List<short[]> chunks = new java.util.ArrayList<>(); int count = 0; short[] chunk;
                while ((chunk = decoder.read()) != null) { chunks.add(chunk); count += chunk.length; }
                pcm[i] = new short[count]; int at = 0;
                for (short[] block : chunks) { System.arraycopy(block, 0, pcm[i], at, block.length); at += block.length; }
                require(count == 44100 && Math.abs(decoder.duration() - 1) < .0001,
                    extension + " chirp retains encoder delay/padding rather than exactly one second of real payload");
            }
        }
        var mp3 = new com.mpatric.mp3agic.Mp3File(tracks[0].getAudio());
        var metadata = Mp3Gapless.read(tracks[0].getAudio(), mp3.getXingOffset());
        require(metadata.metadataFrame() && metadata.skipFrames() == 1105 && metadata.playableFrames() == 44100,
            "Xing/Info encoder sample counts or Layer III decoder delay were not recognized");
        double product = 0, energyA = 0, energyB = 0;
        for (int f = 0; f < 44100; f++) {
            product += pcm[0][f] * (double) pcm[1][f]; energyA += pcm[0][f] * (double) pcm[0][f]; energyB += pcm[1][f] * (double) pcm[1][f];
        }
        require(product / Math.sqrt(energyA * energyB) > .995,
            "MP3 delay trimming is not sample-aligned with the original lossless chirp");
        for (int i = 0; i < 2; i++) {
            Player player = new Player(); player.setCurrentMusic(tracks[i]); player.setPlaying(false); player.seek(.5);
            Thread worker = worker(player);
            try {
                await(() -> !player.isSeeking() && !player.lines.isEmpty()); player.setPlaying(true);
                await(() -> player.completions.get() == 1 && player.lines.getFirst().closed);
                require(Arrays.equals(player.lines.getFirst().samples(), Arrays.copyOfRange(pcm[i], 22050, 44100)),
                    "Encoded-tone seek does not output exactly its post-target MP3/FLAC waveform");
            } finally { stop(player, worker); }
        }
        Player player = new Player(); AtomicInteger transitions = new AtomicInteger();
        player.setPlaybackOptions(true, 0, false); player.setCurrentMusic(tracks[0]); player.prepareNext(tracks[1], transitions::incrementAndGet);
        await(player::hasPreparedNext); Thread worker = worker(player);
        try {
            await(() -> player.completions.get() == 1 && player.lines.getFirst().closed);
            short[] actual = player.lines.getFirst().samples();
            require(player.lines.size() == 1 && transitions.get() == 1 && actual.length == 88200,
                "Real MP3-to-FLAC gapless output adds a line reopen, codec padding or missing frames");
            require(Arrays.equals(Arrays.copyOf(actual, 44100), pcm[0]) && Arrays.equals(Arrays.copyOfRange(actual, 44100, 88200), pcm[1]),
                "Gapless output differs from exact adjacent decoded musical payloads");
        } finally { stop(player, worker); }
        Path malformed = root.resolve("malformed-info.mp3"); Files.write(malformed, new byte[64]);
        require(Mp3Gapless.read(malformed.toFile(), 0).equals(Mp3Gapless.Info.NONE), "Invalid MPEG metadata invents a gapless trim range");
        require(Mp3Gapless.read(tracks[0].getAudio(), -1).equals(Mp3Gapless.Info.NONE), "Missing encoder metadata invents a trim range");
    }

    private static void transition(Path root, boolean fade) throws Exception {
        int frames = 8000;
        short[] a = new short[frames]; Arrays.fill(a, (short) 1000);
        short[] b = new short[fade ? frames : 16000 * 2];
        if (fade) Arrays.fill(b, (short) 3000);
        else for (int f = 0; f < 16000; f++) { b[f * 2] = 4000; b[f * 2 + 1] = 6000; }
        Music first = wave(root, fade ? "fade-a" : "gap-a", 8000, 1, a);
        Music second = wave(root, fade ? "fade-b" : "gap-b", fade ? 8000 : 16000, fade ? 1 : 2, b);
        Player player = new Player(); AtomicInteger claims = new AtomicInteger(), transitions = new AtomicInteger();
        player.setPlaybackOptions(true, fade ? .25f : 0, false); player.setCurrentMusic(first);
        player.prepareNext(second, () -> { claims.incrementAndGet(); return true; }, transitions::incrementAndGet);
        await(player::hasPreparedNext);
        Thread worker = worker(player);
        try {
            await(() -> player.completions.get() == 1 && !player.lines.isEmpty() && player.lines.getFirst().closed);
            short[] pcm = player.lines.getFirst().samples();
            require(player.lines.size() == 1, "Prepared transition opened another line and introduced an output gap");
            require(claims.get() == 1 && transitions.get() == 1 && player.getCurrentMusic() == second, "Transition was not claimed exactly once or metadata remained on the old track");
            require(player.lines.getFirst().drains.get() == 1, "Transition drained the line between songs");
            require(pcm.length == (fade ? 14000 : 16000), "Transition duplicated, omitted or inserted PCM frames");
            require(pcm[0] == (fade ? 0 : 1000) && pcm[5999] == 1000, "Initial PCM fade or outgoing plateau is incorrect");
            if (fade) {
                require(pcm[480] > 450 && pcm[480] < 550 && pcm[959] == 1000,
                    "Configured fade-in is not applied to actual initial PCM");
                require(pcm[6000] == 1000 && Math.abs(pcm[7000] - 2000) <= 2 && pcm[7999] == 3000,
                    "Crossfade does not actually contain both PCM streams with complementary gains");
                require(pcm[pcm.length - 1] == 0, "Configured fade-out did not reach silence");
            } else require(pcm[7999] == 1000 && pcm[8000] == 5000 && pcm[15999] == 5000,
                "Gapless rate/channel conversion lost the exact adjacent PCM boundary");
        } finally { stop(player, worker); }
    }

    private static void rejectAndCancelTransition(Path root) throws Exception {
        short[] a = new short[8000], b = new short[8000]; Arrays.fill(a, (short) 1100); Arrays.fill(b, (short) 2200);
        Music first = wave(root, "reject-a", 8000, 1, a), second = wave(root, "reject-b", 8000, 1, b);
        for (boolean cancel : new boolean[] { false, true }) {
            Player player = new Player(); AtomicInteger claims = new AtomicInteger(), transitions = new AtomicInteger();
            player.setPlaybackOptions(true, 0, false); player.setCurrentMusic(first);
            player.prepareNext(second, () -> { claims.incrementAndGet(); return false; }, transitions::incrementAndGet);
            await(player::hasPreparedNext); if (cancel) player.clearNext();
            Thread worker = worker(player);
            try {
                await(() -> player.completions.get() == 1 && player.lines.getFirst().closed);
                require(Arrays.equals(player.lines.getFirst().samples(), a), "Rejected/cancelled transition lost old PCM or played an unclaimed next song");
                require(transitions.get() == 0 && claims.get() == (cancel ? 0 : 1), "Cancelled preparation invoked a queue claim/transition");
                require(player.getCurrentMusic() == first, "Rejected next track replaced current metadata");
            } finally { stop(player, worker); }
        }
        Player player = new Player(); player.setPlaybackOptions(true, 0, false); player.setCurrentMusic(first);
        player.prepareNext(new Music(root.resolve("missing.wav").toFile(), "Missing", "", null, Color.BLACK), () -> { throw new AssertionError("Failed audio claimed the queue"); });
        Thread worker = worker(player);
        try { await(() -> player.completions.get() == 1); require(player.getCurrentMusic() == first, "Failed next decode prevented ordinary completion"); }
        finally { stop(player, worker); }
    }

    private static void liveOptions(Path root, boolean enable) throws Exception {
        short[] a = new short[8000], b = new short[8000]; Arrays.fill(a, (short) 1000); Arrays.fill(b, (short) 3000);
        Music first = wave(root, "live-options-a-" + enable, 8000, 1, a), second = wave(root, "live-options-b-" + enable, 8000, 1, b);
        AtomicBoolean blocked = new AtomicBoolean(); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Runnable beforeWrite = () -> {
            if (!blocked.compareAndSet(false, true)) return;
            entered.countDown();
            try { if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("Live option write was not released"); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        };
        Player player = new Player() {
            @Override protected SourceDataLine createLine(AudioFormat format) {
                Line line = new Line(format, beforeWrite); lines.add(line); return line.line;
            }
        };
        AtomicInteger transitions = new AtomicInteger();
        player.setPlaybackOptions(true, enable ? 0 : .25f, false); player.setCurrentMusic(first);
        Thread worker = worker(player);
        try {
            require(entered.await(3, TimeUnit.SECONDS), "Live settings test did not reach output");
            player.setPlaybackOptions(true, enable ? .25f : 0, false);
            player.prepareNext(second, transitions::incrementAndGet); await(player::hasPreparedNext); release.countDown();
            await(() -> player.completions.get() == 1 && player.lines.getFirst().closed);
            short[] actual = player.lines.getFirst().samples();
            require(player.lines.size() == 1 && transitions.get() == 1 && actual.length == (enable ? 14000 : 16000),
                "Changing crossfade mid-play leaves a stale tail capacity or loses buffered PCM");
            if (enable) {
                require(actual[0] == 1000 && actual[6000] == 1000 && Math.abs(actual[7000] - 2000) <= 2 && actual[7999] == 3000,
                    "Enabling crossfade mid-play does not mix the real outgoing and incoming PCM");
                require(actual[actual.length - 1] == 0, "Enabled crossfade does not apply a final PCM fade");
            } else {
                require(actual[7999] == 1000 && actual[8000] == 3000 && actual[15999] == 3000,
                    "Disabling crossfade mid-play leaves overlap, silence or a stale final fade");
            }
        } finally { release.countDown(); stop(player, worker); }
    }

    private static void normalization(Path root) throws Exception {
        short[] quiet = new short[1000]; Arrays.fill(quiet, (short) 200);
        require(PcmProcessing.levelGain(quiet) == 4, "Quiet leveling exceeds its bounded gain");
        short[] loud = new short[1000]; Arrays.fill(loud, (short) 20000);
        double gain = PcmProcessing.levelGain(loud); PcmProcessing.gain(loud, gain);
        require(loud[0] >= 4100 && loud[0] <= 4150, "PCM RMS target is not approximately -18 dBFS");
        short[] peaks = { 30000, -30000 }; PcmProcessing.gain(peaks, 4);
        require(peaks[0] == 32767 && peaks[1] == -32768, "Later peaks wrap rather than being safely limited");
        require(PcmProcessing.levelGain(new short[100]) == 1, "Silence causes unbounded or non-finite normalization");
        short[] source = new short[8000]; Arrays.fill(source, (short) 9000);
        Music track = wave(root, "normalization", 8000, 1, source);
        Player player = new Player(); player.setPlaybackOptions(false, 0, true); player.setCurrentMusic(track);
        Thread worker = worker(player);
        try {
            await(() -> player.completions.get() == 1 && player.lines.getFirst().closed);
            short[] pcm = player.lines.getFirst().samples();
            require(pcm.length == source.length && pcm[0] >= 4100 && pcm[0] <= 4150 && pcm[pcm.length - 1] == pcm[0],
                "Leveling is not applied to actual output PCM or consumes its analyzed prefix");
        } finally { stop(player, worker); }
        Player limits = new Player();
        try {
            limits.setPlaybackOptions(true, 12, false);
            require(limits.getCrossfadeSeconds() == 12, "Player does not honor the UI's twelve-second crossfade maximum");
            limits.setPlaybackOptions(true, 30, false);
            require(limits.getCrossfadeSeconds() == 12, "Crossfade length is not bounded at the shared setting maximum");
        } finally { limits.shutdown(); }
    }

    private static void preparedOwnership(Path root, boolean fail) throws Exception {
        short[] pcm = new short[8000]; Arrays.fill(pcm, (short) 1000);
        Music first = wave(root, fail ? "failed-conversion" : "owned-cancel", 8000, 1, pcm);
        Music second = new Music(root.resolve("injected-next.wav").toFile(), "Injected next", "PCM fixture", null, Color.BLACK);
        AtomicInteger reads = new AtomicInteger(), closes = new AtomicInteger(), claims = new AtomicInteger(), transitions = new AtomicInteger();
        AtomicBoolean reading = new AtomicBoolean(), closedDuringRead = new AtomicBoolean();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        PcmDecoder following = new PcmDecoder() {
            public AudioFormat format() { return new AudioFormat(8000, 16, 1, true, false); }
            public double duration() { return 2; }
            public short[] read() throws Exception {
                int count = reads.incrementAndGet();
                if (count == 1) { short[] head = new short[8000]; Arrays.fill(head, (short) 2000); return head; }
                if (count > 2) return null;
                reading.set(true); entered.countDown();
                try {
                    if (!release.await(3, TimeUnit.SECONDS)) throw new java.io.IOException("Blocked decoder was not released");
                    if (fail) throw new java.io.IOException("Corrupt frame after the valid prefetched PCM prefix");
                    short[] rest = new short[8000]; Arrays.fill(rest, (short) 2000); return rest;
                } finally { reading.set(false); }
            }
            public void close() { if (reading.get()) closedDuringRead.set(true); closes.incrementAndGet(); }
        };
        Player player = new Player() {
            @Override protected PcmDecoder openDecoder(Music music) throws Exception {
                return music == second ? following : super.openDecoder(music);
            }
        };
        player.setPlaybackOptions(true, 1, false); player.setCurrentMusic(first);
        player.prepareNext(second, () -> { claims.incrementAndGet(); return true; }, transitions::incrementAndGet);
        await(player::hasPreparedNext); Thread worker = worker(player);
        try {
            require(entered.await(3, TimeUnit.SECONDS), "Next PCM conversion never reached its decoder boundary");
            if (!fail) {
                player.clearNext();
                require(closes.get() == 0 && !closedDuringRead.get(), "Cancellation closes the decoder already owned by the audio worker");
            }
            release.countDown();
            await(() -> player.completions.get() == 1 && player.lines.getFirst().closed);
            short[] actual = player.lines.getFirst().samples();
            require(actual.length == 8000 && actual[0] == 0 && actual[1000] > 800 && actual[1000] <= 1000,
                "Cancelled/corrupt prepared audio loses or replaces the outgoing PCM tail");
            require(claims.get() == 0 && transitions.get() == 0 && player.getCurrentMusic() == first,
                "Cancelled/corrupt next conversion claims the queue or adopts stale metadata");
            require(closes.get() == 1 && !closedDuringRead.get(), "Transferred next decoder is not closed exactly once after the worker finishes reading");
            require(actual[7999] == (fail ? 0 : 1000), "Outgoing fallback fade or cancellation waveform is incorrect");
        } finally { release.countDown(); stop(player, worker); }
    }

    private static void conversion(Path root) throws Exception {
        short[] ramp = new short[8000]; for (int f = 0; f < ramp.length; f++) ramp[f] = (short) (f % 100);
        Music track = wave(root, "resampling", 8000, 1, ramp);
        try (var stream = new PcmStream(PcmDecoders.open(track.getAudio()), new short[0], new AudioFormat(16000, 16, 2, true, false))) {
            List<short[]> chunks = new java.util.ArrayList<>(); int samples = 0; short[] block;
            while ((block = stream.read(997)) != null) { chunks.add(block); samples += block.length; }
            require(samples == 32000, "Upsampling duration changes across non-aligned read boundaries");
            short[] all = new short[samples]; int at = 0;
            for (short[] chunk : chunks) { System.arraycopy(chunk, 0, all, at, chunk.length); at += chunk.length; }
            require(all[0] == all[1] && all[100] == all[101], "Mono-to-stereo conversion does not duplicate channels");
            require(all[4] == 1 && all[5] == 1, "Resampling phase restarted between buffers");
        }
        for (int[] rates : new int[][] { {44100, 48000}, {48000, 44100}, {22050, 48000} }) {
            short[] tone = new short[rates[0]]; Arrays.fill(tone, (short) 1234);
            Music converted = wave(root, "rational-rate-" + rates[0] + "-" + rates[1], rates[0], 1, tone);
            try (var stream = new PcmStream(PcmDecoders.open(converted.getAudio()), new short[0], new AudioFormat(rates[1], 16, 1, true, false))) {
                int count = 0; short[] block;
                while ((block = stream.read(997)) != null) {
                    count += block.length;
                    if (block[0] != 1234 || block[block.length - 1] != 1234) throw new AssertionError("Fractional-ratio conversion changes constant PCM");
                }
                require(count == rates[1], "Fractional-ratio resampling drifts beyond the exact one-second PCM payload");
            }
        }
    }
    private static Music wave(Path root, String name, int rate, int channels, short[] pcm) throws Exception {
        Path path = root.resolve(name + ".wav"); AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
        try (var audio = new AudioInputStream(new ByteArrayInputStream(PcmProcessing.bytes(pcm)), format, pcm.length / channels)) {
            javax.sound.sampled.AudioSystem.write(audio, AudioFileFormat.Type.WAVE, path.toFile());
        }
        return new Music(path.toFile(), name, "PCM fixture", null, Color.BLACK);
    }
    private static Thread worker(MusicPlayer player) {
        return Thread.ofPlatform().daemon().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                player.run(); try { Thread.sleep(2); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        });
    }
    private static void stop(Player player, Thread worker) throws Exception {
        player.shutdown(); worker.interrupt(); worker.join(2000); require(!worker.isAlive(), "Audio worker does not close");
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 4_000_000_000L;
        while (!condition.getAsBoolean()) { if (System.nanoTime() > deadline) throw new AssertionError("Audio fixture timed out"); Thread.sleep(2); }
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static class Player extends MusicPlayer {
        final AtomicInteger completions; final List<Line> lines = new CopyOnWriteArrayList<>();
        Player() { this(new AtomicInteger()); }
        private Player(AtomicInteger count) { super(count::incrementAndGet); completions = count; setVolume(1); }
        @Override protected SourceDataLine createLine(AudioFormat format) { Line line = new Line(format); lines.add(line); return line.line; }
    }
    private static final class Line {
        final AudioFormat format; final SourceDataLine line;
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        final AtomicInteger starts = new AtomicInteger(), flushes = new AtomicInteger(), drains = new AtomicInteger();
        final FloatControl gain = new FloatControl(FloatControl.Type.MASTER_GAIN, -80, 6, .1f, 1, 0, "dB") { };
        volatile boolean closed;
        Line(AudioFormat format) { this(format, () -> { }); }
        Line(AudioFormat format, Runnable beforeWrite) {
            this.format = format;
            line = (SourceDataLine) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { SourceDataLine.class }, (_, method, args) -> {
                switch (method.getName()) {
                    case "write" -> {
                        if (closed) throw new IllegalStateException("Closed fixture output");
                        beforeWrite.run();
                        synchronized (data) { data.write((byte[]) args[0], (int) args[1], (int) args[2]); }
                        Thread.sleep(1); return (int) args[2];
                    }
                    case "start" -> { starts.incrementAndGet(); return null; }
                    case "flush" -> { flushes.incrementAndGet(); return null; }
                    case "drain" -> { drains.incrementAndGet(); return null; }
                    case "close" -> { closed = true; return null; }
                    case "getControl" -> { return gain; }
                    case "getControls" -> { return new javax.sound.sampled.Control[] { gain }; }
                    case "getFormat" -> { return format; }
                    case "getLongFramePosition" -> { return (long) frames(); }
                    case "getFramePosition" -> { return frames(); }
                    case "getMicrosecondPosition" -> { return (long) (frames() * 1_000_000d / format.getFrameRate()); }
                    case "isOpen", "isRunning", "isActive", "isControlSupported" -> { return !closed; }
                    default -> {
                        if (method.getReturnType() == int.class) return 0;
                        if (method.getReturnType() == float.class) return 0f;
                        if (method.getReturnType() == boolean.class) return false;
                        return null;
                    }
                }
            });
        }
        int frames() { synchronized (data) { return data.size() / format.getFrameSize(); } }
        short[] samples() {
            byte[] bytes; synchronized (data) { bytes = data.toByteArray(); }
            short[] samples = new short[bytes.length / 2];
            for (int i = 0; i < samples.length; i++) samples[i] = (short) ((bytes[i * 2] & 255) | bytes[i * 2 + 1] << 8);
            return samples;
        }
    }
}
