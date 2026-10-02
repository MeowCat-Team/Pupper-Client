package cn.pupperclient.management.music.media;

import cn.pupperclient.PupperLogger;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** A single daemon MTA owns SMTC. Rendering never calls native APIs, and unsupported hosts are inert. */
public final class MediaSession implements AutoCloseable {
    private final ScheduledExecutorService worker;
    private volatile boolean closed;
    private WindowsSmtc nativeSession;
    private boolean failed;

    public MediaSession(long window, Supplier<WindowsSmtc.Snapshot> state, IntConsumer action) {
        if (window == 0 || !supported()) { worker = null; return; }
        worker = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("Pupper Client SMTC").factory());
        worker.scheduleWithFixedDelay(() -> {
            if (closed || failed) return;
            try {
                var snapshot = state.get();
                if (nativeSession == null && snapshot.hasTrack()) nativeSession = new WindowsSmtc(window,
                    button -> { if (!closed) action.accept(button); });
                if (nativeSession != null) nativeSession.publish(snapshot);
            } catch (RuntimeException | LinkageError unavailable) {
                failed = true;
                if (nativeSession != null) { nativeSession.close(); nativeSession = null; }
                PupperLogger.warn("Music", "Windows media controls unavailable: " + unavailable.getMessage());
            }
        }, 0, 250, TimeUnit.MILLISECONDS);
    }

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")
            && java.lang.foreign.ValueLayout.ADDRESS.byteSize() == 8;
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (worker == null) return;
        worker.execute(() -> { if (nativeSession != null) { nativeSession.close(); nativeSession = null; } });
        worker.shutdown();
        try { worker.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
