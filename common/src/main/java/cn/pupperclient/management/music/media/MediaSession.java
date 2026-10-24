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

package cn.pupperclient.management.music.media;

import cn.pupperclient.PupperLogger;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/** A single daemon MTA owns SMTC. Rendering never calls native APIs, and unsupported hosts are inert. */
public final class MediaSession implements AutoCloseable {
    private final ScheduledExecutorService worker;
    private volatile boolean closed;
    private WindowsSmtc nativeSession;
    private boolean failed;

    public MediaSession(long window, Supplier<WindowsSmtc.Snapshot> state, IntConsumer action) {
        this(window, state, action, null);
    }
    public MediaSession(long window, Supplier<WindowsSmtc.Snapshot> state, IntConsumer action, DoubleConsumer seek) {
        if (window == 0 || !supported()) { worker = null; return; }
        worker = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("Pupper Client SMTC").factory());
        worker.scheduleWithFixedDelay(() -> {
            if (closed || failed) return;
            try {
                var snapshot = state.get();
                if (nativeSession == null && snapshot.hasTrack()) nativeSession = new WindowsSmtc(window,
                    button -> { if (!closed) action.accept(button); },
                    seek == null ? null : seconds -> { if (!closed) seek.accept(seconds); });
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
