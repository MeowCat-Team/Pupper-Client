package cn.pupperclient.management.music;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Bounded explicit downloads. Credentials stay in private requests, never in task snapshots or errors. */
public final class MusicDownloadTasks implements AutoCloseable {
    public enum State { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }
    public record Task(long id, MusicTrack track, String quality, State state, int progress,
            String errorKey, Path audio, int attempts) {
        public boolean active() { return state == State.QUEUED || state == State.RUNNING; }
        public boolean retryable() { return state == State.FAILED || state == State.CANCELLED; }
    }
    @FunctionalInterface public interface Transfer {
        MusicDownload.Result download(MusicTrack track, String quality, String credentials, IntConsumer progress,
            MusicPreparation.Cancellation cancellation) throws Exception;
    }
    private record Key(String track, String quality, String credentials) { }
    private record Subscriber(Consumer<MusicDownload.Result> success, Consumer<MusicError> failure) { }
    private static final class Entry {
        final long id;
        MusicTrack track;
        final String quality;
        String credentials;
        Key key;
        State state = State.QUEUED;
        int progress, attempts;
        String error = "";
        Path audio;
        Job job;
        final List<Subscriber> subscribers = new ArrayList<>();
        Entry(long id, MusicTrack track, String quality, String credentials) {
            this.id = id; this.track = track; this.quality = quality; this.credentials = credentials;
            key = key(track, quality, credentials);
        }
        Task snapshot() { return new Task(id, track, quality, state, progress, error, audio, attempts); }
    }
    private static final class Job {
        final Entry entry;
        final MusicTrack track;
        final String credentials;
        final MusicPreparation.Cancellation cancellation = new MusicPreparation.Cancellation();
        Job(Entry entry) { this.entry = entry; track = entry.track; credentials = entry.credentials; }
    }
    private final Object lock = new Object();
    private final Map<Long, Entry> entries = new LinkedHashMap<>();
    private final Map<Key, Entry> active = new LinkedHashMap<>();
    private final ArrayDeque<Entry> waiting = new ArrayDeque<>();
    private final Executor worker, callbacks;
    private final Transfer transfer;
    private final Consumer<MusicDownload.Result> completed;
    private final int parallelism;
    private long sequence;
    private int running;
    private boolean closed;

    /** Executors remain owned by the caller. Completion and subscriber callbacks run on callbacks. */
    public MusicDownloadTasks(Executor worker, Executor callbacks, Transfer transfer, Consumer<MusicDownload.Result> completed) {
        this(worker, callbacks, transfer, completed, 2);
    }
    public MusicDownloadTasks(Executor worker, Executor callbacks, Transfer transfer,
            Consumer<MusicDownload.Result> completed, int parallelism) {
        this.worker = Objects.requireNonNull(worker); this.callbacks = Objects.requireNonNull(callbacks);
        this.transfer = Objects.requireNonNull(transfer); this.completed = Objects.requireNonNull(completed);
        if (parallelism < 1 || parallelism > 8) throw new IllegalArgumentException("Invalid download concurrency");
        this.parallelism = parallelism;
    }
    public long submit(MusicTrack track, String quality, String credentials) {
        return submit(track, quality, credentials, _ -> { }, _ -> { });
    }
    /** Joining an active task still delivers each caller's result, without a second transfer. */
    public long submit(MusicTrack track, String quality, String credentials,
            Consumer<MusicDownload.Result> success, Consumer<MusicError> failure) {
        Objects.requireNonNull(track); Objects.requireNonNull(quality);
        String credential = credentials == null ? "" : credentials;
        List<Job> starts; long id;
        synchronized (lock) {
            if (closed) throw new IllegalStateException("Music downloads closed");
            Entry entry = active.get(key(track, quality, credential));
            if (entry == null) {
                entry = new Entry(++sequence, track, quality, credential);
                entries.put(entry.id, entry); active.put(entry.key, entry); waiting.addLast(entry);
            }
            entry.subscribers.add(new Subscriber(Objects.requireNonNull(success), Objects.requireNonNull(failure)));
            id = entry.id; starts = startLocked();
        }
        execute(starts); return id;
    }
    public List<Long> submitBatch(Collection<MusicTrack> tracks, String quality, String credentials) {
        var unique = new LinkedHashMap<String, MusicTrack>();
        tracks.forEach(track -> unique.putIfAbsent(track.key(), track));
        return unique.values().stream().map(track -> submit(track, quality, credentials)).toList();
    }
    public List<Task> list() {
        synchronized (lock) { return entries.values().stream().map(Entry::snapshot).toList(); }
    }
    public int progress(MusicTrack track) {
        synchronized (lock) {
            return entries.values().stream().filter(entry -> entry.track.sameSong(track)
                && (entry.state == State.RUNNING || entry.state == State.QUEUED)).mapToInt(entry -> entry.progress).max().orElse(-1);
        }
    }
    public boolean cancel(long id) {
        Job job; List<Subscriber> subscribers; List<Job> starts;
        synchronized (lock) {
            Entry entry = entries.get(id);
            if (entry == null || (entry.state != State.RUNNING && entry.state != State.QUEUED)) return false;
            job = entry.job; entry.state = State.CANCELLED; entry.error = "music.downloads.cancelled";
            active.remove(entry.key, entry); waiting.remove(entry);
            subscribers = List.copyOf(entry.subscribers); entry.subscribers.clear(); starts = startLocked();
        }
        if (job != null) job.cancellation.cancel();
        failed(subscribers, new MusicError("music.downloads.cancelled")); execute(starts); return true;
    }
    public int cancelAll() {
        List<Job> jobs = new ArrayList<>(); List<Subscriber> subscribers = new ArrayList<>(); int count = 0;
        synchronized (lock) {
            for (Entry entry : entries.values()) if (entry.state == State.RUNNING || entry.state == State.QUEUED) {
                count++; entry.state = State.CANCELLED; entry.error = "music.downloads.cancelled";
                if (entry.job != null) jobs.add(entry.job);
                subscribers.addAll(entry.subscribers); entry.subscribers.clear();
            }
            active.clear(); waiting.clear();
        }
        jobs.forEach(job -> job.cancellation.cancel()); failed(subscribers, new MusicError("music.downloads.cancelled")); return count;
    }
    public boolean retry(long id) {
        String credentials;
        synchronized (lock) {
            Entry entry = entries.get(id); if (entry == null) return false;
            credentials = entry.credentials;
        }
        return retryLockedCredentials(id, credentials);
    }
    public boolean retry(long id, String credentials) {
        return retryLockedCredentials(id, credentials == null ? "" : credentials);
    }
    private boolean retryLockedCredentials(long id, String credentials) {
        List<Job> starts;
        synchronized (lock) {
            Entry entry = entries.get(id);
            if (closed || entry == null || (entry.state != State.FAILED && entry.state != State.CANCELLED)) return false;
            Key key = key(entry.track, entry.quality, credentials);
            if (active.containsKey(key)) return false;
            entry.credentials = credentials; entry.key = key; entry.state = State.QUEUED; entry.progress = 0; entry.error = ""; entry.audio = null;
            active.put(key, entry); waiting.addLast(entry); starts = startLocked();
        }
        execute(starts); return true;
    }
    public int retryFailed() {
        int count = 0;
        for (Task task : list()) if (task.state() == State.FAILED && retry(task.id())) count++;
        return count;
    }
    /** Failed rows remain visible until retried; clearing successful/cancelled history is explicit. */
    public int clearFinished() {
        synchronized (lock) {
            int before = entries.size();
            entries.values().removeIf(entry -> entry.state == State.COMPLETED || entry.state == State.CANCELLED);
            return before - entries.size();
        }
    }
    private List<Job> startLocked() {
        List<Job> starts = new ArrayList<>();
        while (!closed && running < parallelism && !waiting.isEmpty()) {
            Entry entry = waiting.removeFirst();
            if (entry.state != State.QUEUED) continue;
            Job job = new Job(entry); entry.job = job; entry.state = State.RUNNING; entry.attempts++; running++; starts.add(job);
        }
        return starts;
    }
    private void execute(List<Job> jobs) {
        for (Job job : jobs) try { worker.execute(() -> run(job)); }
        catch (RuntimeException rejected) { finish(job, null, rejected); }
    }
    private void run(Job job) {
        MusicDownload.Result result = null; Throwable error = null;
        try {
            job.cancellation.check();
            if (!job.track.downloadable()) throw new MusicError("music.error.downloadrestricted");
            result = Objects.requireNonNull(transfer.download(job.track, job.entry.quality, job.credentials, value -> {
                synchronized (lock) {
                    if (job.entry.job == job && job.entry.state == State.RUNNING)
                        job.entry.progress = Math.max(job.entry.progress, Math.clamp(value, 0, 99));
                }
            }, job.cancellation));
        } catch (Exception failure) { error = failure; }
        finally { finish(job, result, error); }
    }
    private void finish(Job job, MusicDownload.Result result, Throwable error) {
        List<Subscriber> subscribers = List.of(); List<Job> starts; boolean success = false; MusicError failure = null;
        synchronized (lock) {
            running--;
            Entry entry = job.entry;
            if (entry.job == job && entry.state == State.RUNNING) {
                entry.job = null; active.remove(entry.key, entry);
                subscribers = List.copyOf(entry.subscribers); entry.subscribers.clear();
                success = error == null && result != null;
                if (success) {
                    entry.state = State.COMPLETED; entry.progress = 100; entry.track = result.track(); entry.audio = result.audio();
                } else {
                    failure = error instanceof MusicError musicError ? musicError
                        : new MusicError(error instanceof IOException ? "music.error.file" : "music.error.network");
                    entry.state = State.FAILED; entry.error = failure.key();
                }
            }
            starts = startLocked();
        }
        if (success) {
            var listeners = subscribers; var downloaded = result;
            dispatch(() -> {
                safely(() -> completed.accept(downloaded));
                for (Subscriber listener : listeners) safely(() -> listener.success().accept(downloaded));
            });
        } else if (failure != null) failed(subscribers, failure);
        execute(starts);
    }
    private void failed(List<Subscriber> subscribers, MusicError error) {
        if (!subscribers.isEmpty()) dispatch(() -> {
            for (Subscriber subscriber : subscribers) safely(() -> subscriber.failure().accept(error));
        });
    }
    private void dispatch(Runnable action) {
        try { callbacks.execute(() -> {
            synchronized (lock) { if (closed) return; }
            action.run();
        }); }
        catch (RuntimeException unavailable) { /* Shutdown cannot strand worker slots or re-run callbacks. */ }
    }
    private static void safely(Runnable action) { try { action.run(); } catch (RuntimeException ignored) { } }
    private static Key key(MusicTrack track, String quality, String credentials) {
        try {
            return new Key(track.key(), quality, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(credentials.getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    @Override public void close() {
        synchronized (lock) { if (closed) return; closed = true; }
        cancelAll();
    }
}
