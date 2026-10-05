package cn.pupperclient.management.music;

import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.management.music.audio.PcmDecoder;
import cn.pupperclient.management.music.audio.PcmDecoders;
import cn.pupperclient.management.music.audio.PcmProcessing;
import cn.pupperclient.management.music.audio.PcmStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

/** One decoder worker owns PCM/output. Seek invalidates its session; prepared transitions reuse its line. */
public class MusicPlayer implements Runnable {
    public static final int SPECTRUM_BANDS = 100;
    public static float[] VISUALIZER = new float[SPECTRUM_BANDS];
    public static SimpleAnimation[] ANIMATIONS = new SimpleAnimation[SPECTRUM_BANDS];
    static { for (int i = 0; i < SPECTRUM_BANDS; i++) ANIMATIONS[i] = new SimpleAnimation(); }
    private static final int BLOCK_FRAMES = 2048, MAX_HEAD_BYTES = 4 * 1024 * 1024;
    private final Runnable completion;
    private final ThreadPoolExecutor preparation = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(), Thread.ofPlatform().daemon().name("Pupper Client PCM preparation").factory());
    private volatile Music currentMusic;
    private volatile boolean playing, repeat, seeking, closed;
    private volatile float volume = .5f, duration, lastCurrentTime, crossfadeSeconds;
    private volatile boolean gapless, normalize;
    private volatile boolean hardwareGain;
    private volatile long generation;
    private volatile SourceDataLine sourceDataLine;
    private volatile AudioFormat audioFormat;
    private volatile double positionOffset;
    private volatile long positionOrigin;
    private double requestedPosition;
    private boolean running;
    private Next next;

    private record Head(PcmDecoder decoder, short[] samples, double gain) implements AutoCloseable {
        @Override public void close() throws Exception { decoder.close(); }
    }
    private static final class Next {
        final Music music, from;
        final long generation;
        final Runnable transition;
        final BooleanSupplier claim;
        Future<?> task;
        Head head;
        boolean cancelled;
        Next(Music music, Music from, long generation, BooleanSupplier claim, Runnable transition) {
            this.music = music; this.from = from; this.generation = generation; this.claim = claim; this.transition = transition;
        }
    }
    public MusicPlayer(Runnable completion) { this.completion = completion; }
    public void setPlaybackOptions(boolean gapless, float seconds, boolean normalize) {
        this.gapless = gapless; crossfadeSeconds = Float.isFinite(seconds) ? Math.clamp(seconds, 0, 12) : 0;
        this.normalize = normalize; clearNext();
    }
    public boolean isGapless() { return gapless; }
    public float getCrossfadeSeconds() { return crossfadeSeconds; }
    public boolean isNormalize() { return normalize; }
    public boolean isSeeking() { return seeking; }
    public Music getCurrentMusic() { return currentMusic; }

    /** Accept immediately; really decode/discard to the requested sample on the worker, preserving pause. */
    public boolean seek(double seconds) {
        if (!Double.isFinite(seconds)) return false;
        SourceDataLine old;
        synchronized (this) {
            if (closed || currentMusic == null) return false;
            double end = duration > 0 ? duration : currentMusic.getTrack().durationMillis() / 1000d;
            requestedPosition = Math.max(0, end > 0 ? Math.min(seconds, end) : seconds);
            lastCurrentTime = (float) requestedPosition; positionOffset = requestedPosition; positionOrigin = 0;
            generation++; seeking = true; old = detachLine(); notifyAll();
        }
        closeOutput(old); clearNext(); return true;
    }
    public void prepareNext(Music music, Runnable transition) { prepareNext(music, () -> true, transition); }
    /** Claim executes once under the player lock, after conversion: it may atomically advance a queue, never call this player. */
    public void prepareNext(Music music, BooleanSupplier claim, Runnable transition) {
        if (music == null || transition == null || claim == null) { clearNext(); return; }
        Next request, previous;
        synchronized (this) {
            if (closed || currentMusic == null || (!gapless && crossfadeSeconds == 0) || repeat) return;
            if (next != null && !next.cancelled && next.generation == generation && next.music.getAudio().equals(music.getAudio())) return;
            previous = next; request = new Next(music, currentMusic, generation, claim, transition); next = request;
        }
        cancel(previous);
        Future<?> task;
        try {
            task = preparation.submit(() -> {
                Head head = null;
                try {
                    head = head(openDecoder(music), Math.max(1, normalize ? 10 : crossfadeSeconds),
                        () -> !Thread.currentThread().isInterrupted() && alive(request));
                    if (head.samples().length == 0) throw new java.io.IOException("Prepared audio has no PCM");
                    synchronized (this) {
                        if (next == request && !request.cancelled && !closed) { request.head = head; head = null; }
                    }
                } catch (Exception unavailable) { /* Preparation failure falls back to ordinary completion. */ }
                finally { closeQuietly(head); }
            });
        } catch (RejectedExecutionException stopped) { synchronized (this) { if (next == request) next = null; } return; }
        synchronized (this) { request.task = task; if (request.cancelled) task.cancel(true); }
    }
    public void clearNext() {
        Next previous;
        synchronized (this) { previous = next; next = null; if (previous != null) previous.cancelled = true; }
        cancel(previous);
    }
    public synchronized boolean hasPreparedNext() { return next != null && next.head != null && !next.cancelled; }
    private synchronized boolean alive(Next request) { return !closed && !request.cancelled && next == request && request.generation == generation; }
    private void cancel(Next request) {
        if (request == null) return;
        Head head;
        synchronized (this) { request.cancelled = true; head = request.head; request.head = null; if (request.task != null) request.task.cancel(true); }
        preparation.purge(); closeQuietly(head);
    }

    @Override public void run() {
        Music track; long session; double start;
        synchronized (this) {
            if (running || closed || currentMusic == null || (!playing && !seeking)) return;
            running = true; track = currentMusic; session = generation; start = requestedPosition;
        }
        SourceDataLine line = null;
        PcmStream stream = null, spare = null;
        try {
            final long openingSession = session;
            Head head = head(openDecoder(track), normalize ? 10 : 0, () -> valid(openingSession));
            AudioFormat source = head.decoder().format();
            AudioFormat output = new AudioFormat(source.getSampleRate(), 16, Math.min(2, source.getChannels()), true, false);
            stream = new PcmStream(head.decoder(), head.samples(), output);
            double gain = head.gain();
            long skip = Math.max(0, Math.round(start * output.getSampleRate())), discarded = 0;
            while (discarded < skip && valid(session)) {
                short[] block = stream.read((int) Math.min(BLOCK_FRAMES, skip - discarded));
                if (block == null) break;
                discarded += block.length / output.getChannels();
            }
            if (!valid(session)) return;
            line = createLine(output); line.open(output);
            synchronized (this) {
                if (!valid(session)) return;
                audioFormat = output; sourceDataLine = line;
                positionOrigin = line.getLongFramePosition(); positionOffset = discarded / (double) output.getSampleRate();
                duration = (float) head.decoder().duration(); lastCurrentTime = (float) positionOffset; seeking = false;
                setVolume(volume); if (playing) line.start();
            }
            long written = 0, lineBase = line.getLongFramePosition();
            int holdSamples = holdSamples(output);
            int fadeInFrames = start == 0 && crossfadeSeconds > 0
                ? Math.min(Math.round(.12f * output.getSampleRate()), holdSamples / output.getChannels()) : 0;
            long fadedFrames = 0;
            Tail tail = new Tail(holdSamples);
            while (valid(session)) {
                if (!awaitPlaying(session)) return;
                int requestedHold = holdSamples(output);
                if (requestedHold != holdSamples) {
                    short[] retained = tail.finish();
                    tail = new Tail(requestedHold); holdSamples = requestedHold;
                    written += write(line, tail.offer(retained), output, session);
                }
                short[] block = stream.read(BLOCK_FRAMES);
                if (block != null) {
                    PcmProcessing.gain(block, gain);
                    PcmProcessing.fadeIn(block, output.getChannels(), fadedFrames, fadeInFrames);
                    fadedFrames += block.length / output.getChannels();
                    written += write(line, tail.offer(block), output, session);
                    continue;
                }
                short[] remaining = tail.finish();
                Next transition;
                Head following;
                synchronized (this) {
                    transition = next;
                    if (repeat || transition == null || transition.cancelled || transition.head == null
                            || transition.generation != session || transition.from != currentMusic) transition = null;
                    following = transition == null ? null : transition.head;
                    // Transfer decoder ownership to this worker; cancellation may mark the request but cannot close it concurrently.
                    if (following != null) transition.head = null;
                }
                if (following == null) {
                    fadeOut(remaining, output.getChannels(), crossfadeSeconds > 0);
                    written += write(line, remaining, output, session);
                    if (!valid(session) || !awaitPlaying(session)) return;
                    line.drain(); complete(session); return;
                }
                short[] incoming;
                float followingDuration;
                try {
                    spare = new PcmStream(following.decoder(), following.samples(), output);
                    int overlapFrames = Math.min(remaining.length / output.getChannels(), Math.round(crossfadeSeconds * output.getSampleRate()));
                    incoming = overlapFrames == 0 ? new short[0] : spare.read(overlapFrames);
                    if (incoming == null) incoming = new short[0];
                    PcmProcessing.gain(incoming, following.gain());
                    followingDuration = (float) following.decoder().duration();
                } catch (Exception unusableNext) {
                    // A prepared prefix may be valid while its next codec frame is corrupt. Never claim that song.
                    if (spare == null) closeQuietly(following); else closeQuietly(spare);
                    spare = null;
                    synchronized (this) {
                        if (next == transition) next = null;
                        transition.cancelled = true;
                    }
                    fadeOut(remaining, output.getChannels(), crossfadeSeconds > 0);
                    written += write(line, remaining, output, session);
                    if (valid(session) && awaitPlaying(session)) { line.drain(); complete(session); }
                    return;
                }
                int overlapSamples = incoming.length, outgoingPrefix = remaining.length - overlapSamples;
                written += write(line, Arrays.copyOf(remaining, outgoingPrefix), output, session);
                if (!awaitPlaying(session)) return;
                Runnable notify;
                synchronized (this) {
                    if (!valid(session) || next != transition || transition.cancelled || !transition.claim.getAsBoolean()) {
                        transition = null; notify = null;
                    } else {
                        next = null; transition.cancelled = true;
                        currentMusic = transition.music; requestedPosition = 0; session = ++generation;
                        positionOrigin = lineBase + written; positionOffset = 0; lastCurrentTime = 0;
                        duration = followingDuration; notify = transition.transition;
                    }
                }
                if (transition == null) {
                    closeQuietly(spare); spare = null;
                    written += write(line, Arrays.copyOfRange(remaining, outgoingPrefix, remaining.length), output, session);
                    if (valid(session)) { line.drain(); complete(session); }
                    return;
                }
                closeQuietly(stream); stream = spare; spare = null; gain = following.gain();
                fadeInFrames = 0;
                try { notify.run(); } catch (RuntimeException callbackFailure) { cn.pupperclient.PupperLogger.error("MusicPlayer", "Transition callback failed", callbackFailure); }
                if (overlapSamples > 0) written += write(line,
                    PcmProcessing.crossfade(Arrays.copyOfRange(remaining, outgoingPrefix, remaining.length), incoming, output.getChannels()), output, session);
                tail = new Tail(holdSamples);
            }
        } catch (Exception failure) {
            if (valid(session) && !(failure instanceof InterruptedException)) {
                playing = false; seeking = false;
                cn.pupperclient.PupperLogger.error("MusicPlayer", "Audio playback failed", failure);
            }
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            closeQuietly(spare); closeQuietly(stream); releaseLine(line);
            synchronized (this) { running = false; }
        }
    }
    protected PcmDecoder openDecoder(Music track) throws Exception { return PcmDecoders.open(track.getAudio()); }
    private int holdSamples(AudioFormat output) {
        int samples = Math.min(MAX_HEAD_BYTES / 2, Math.round(crossfadeSeconds * output.getSampleRate()) * output.getChannels());
        return samples - samples % output.getChannels();
    }
    private Head head(PcmDecoder decoder, float seconds, BooleanSupplier valid) throws Exception {
        boolean keep = false;
        try {
            List<short[]> blocks = new ArrayList<>();
            int count = 0, limit = Math.min(MAX_HEAD_BYTES / 2, Math.round(seconds * decoder.format().getSampleRate()) * decoder.format().getChannels());
            while (count < limit) {
                if (!valid.getAsBoolean()) throw new java.io.InterruptedIOException("Cancelled PCM preparation");
                short[] block = decoder.read(); if (block == null) break;
                blocks.add(block); count += block.length;
            }
            short[] samples = new short[count]; int at = 0;
            for (short[] block : blocks) { System.arraycopy(block, 0, samples, at, block.length); at += block.length; }
            keep = true; return new Head(decoder, samples, normalize ? PcmProcessing.levelGain(samples) : 1);
        } finally { if (!keep) decoder.close(); }
    }
    private boolean valid(long session) { return !closed && generation == session && !Thread.currentThread().isInterrupted(); }
    private synchronized boolean awaitPlaying(long session) throws InterruptedException {
        while (!playing && valid(session)) wait(20);
        return valid(session);
    }
    private long write(SourceDataLine line, short[] samples, AudioFormat format, long session) throws Exception {
        int samplesPerBlock = BLOCK_FRAMES * format.getChannels(); long frames = 0;
        for (int at = 0; at < samples.length && valid(session); at += samplesPerBlock) {
            if (!awaitPlaying(session)) break;
            short[] block = Arrays.copyOfRange(samples, at, Math.min(samples.length, at + samplesPerBlock));
            if (!hardwareGain) PcmProcessing.gain(block, volume);
            updateSpectrum(block); byte[] bytes = PcmProcessing.bytes(block); int written = 0;
            while (written < bytes.length && valid(session)) {
                int size = line.write(bytes, written, bytes.length - written);
                if (size <= 0) throw new java.io.IOException("Audio output made no progress");
                written += size;
            }
            frames += written / format.getFrameSize();
        }
        return frames;
    }
    private static void fadeOut(short[] samples, int channels, boolean enabled) {
        if (!enabled || samples.length == 0) return;
        int frames = samples.length / channels;
        for (int f = 0; f < frames; f++) for (int c = 0; c < channels; c++)
            samples[f * channels + c] = PcmProcessing.limit(samples[f * channels + c] * (1 - f / (double) Math.max(1, frames - 1)));
    }
    private void updateSpectrum(short[] samples) {
        for (int i = 0; i < SPECTRUM_BANDS; i++) {
            int from = i * Math.min(1024, samples.length) / SPECTRUM_BANDS, end = (i + 1) * Math.min(1024, samples.length) / SPECTRUM_BANDS;
            double total = 0; for (int j = from; j < end; j++) total += Math.abs(samples[j] / 32768d);
            VISUALIZER[i] = (float) (-120 * total / Math.max(1, end - from));
        }
    }
    public void setCurrentMusic(Music music) {
        SourceDataLine old;
        synchronized (this) {
            generation++; currentMusic = music; playing = music != null; seeking = false;
            requestedPosition = 0; lastCurrentTime = 0; duration = 0; positionOffset = 0; positionOrigin = 0;
            old = detachLine(); notifyAll();
        }
        closeOutput(old); clearNext();
    }
    private void complete(long session) {
        Runnable callback = null;
        synchronized (this) {
            if (!valid(session)) return;
            if (repeat) { generation++; requestedPosition = 0; lastCurrentTime = 0; positionOffset = 0; positionOrigin = 0; }
            else { lastCurrentTime = duration; playing = false; callback = completion; }
        }
        if (callback != null) callback.run();
    }
    public void setRepeat(boolean repeat) { this.repeat = repeat; if (repeat) clearNext(); }
    protected SourceDataLine createLine(AudioFormat format) throws javax.sound.sampled.LineUnavailableException {
        return (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
    }
    public long getGeneration() { return generation; }
    public void shutdown() {
        SourceDataLine old;
        synchronized (this) { closed = true; generation++; playing = false; seeking = false; old = detachLine(); notifyAll(); }
        closeOutput(old); clearNext(); preparation.shutdownNow();
    }
    private SourceDataLine detachLine() { SourceDataLine old = sourceDataLine; sourceDataLine = null; return old; }
    private static void closeOutput(SourceDataLine line) { if (line != null) { line.stop(); line.flush(); line.close(); } }
    private void releaseLine(SourceDataLine line) {
        if (line == null) return;
        synchronized (this) { if (sourceDataLine == line) sourceDataLine = null; }
        line.close();
    }
    public boolean isPlaying() { return playing; }
    public synchronized void setPlaying(boolean value) {
        if (!value) lastCurrentTime = getCurrentTime(); playing = value;
        SourceDataLine line = sourceDataLine;
        if (line != null) { if (value) line.start(); else line.stop(); }
        notifyAll();
    }
    public float getCurrentTime() {
        SourceDataLine line = sourceDataLine; AudioFormat format = audioFormat;
        if (line == null || format == null || !playing || seeking) return lastCurrentTime;
        float time = (float) (positionOffset + Math.max(0, line.getLongFramePosition() - positionOrigin) / (double) format.getSampleRate());
        lastCurrentTime = duration > 0 ? Math.min(time, duration) : time; return lastCurrentTime;
    }
    public float getEndTime() { return duration; }
    public float getVolume() { return volume; }
    public void setVolume(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1) return;
        volume = value; SourceDataLine line = sourceDataLine;
        if (line != null) try {
            FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            gain.setValue(value == 0 ? gain.getMinimum() : Math.clamp((float) (Math.log10(value) * 20), gain.getMinimum(), gain.getMaximum()));
            hardwareGain = true;
        } catch (IllegalArgumentException unsupported) { hardwareGain = false; }
    }
    private static void closeQuietly(AutoCloseable value) { if (value != null) try { value.close(); } catch (Exception ignored) { } }
    private static final class Tail {
        private final short[] ring; private int start, size;
        Tail(int capacity) { ring = new short[capacity]; }
        short[] offer(short[] samples) {
            int count = Math.max(0, size + samples.length - ring.length), fromRing = Math.min(size, count);
            short[] ready = new short[count];
            for (int i = 0; i < fromRing; i++) { ready[i] = ring[start]; start = (start + 1) % ring.length; size--; }
            int fromInput = count - fromRing; System.arraycopy(samples, 0, ready, fromRing, fromInput);
            for (int i = fromInput; i < samples.length; i++) { ring[(start + size) % ring.length] = samples[i]; size++; }
            return ready;
        }
        short[] finish() {
            short[] result = new short[size];
            for (int i = 0; i < result.length; i++) result[i] = ring[(start + i) % ring.length];
            size = 0; return result;
        }
    }
}
