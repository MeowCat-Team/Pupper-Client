package cn.pupperclient.music;

import cn.pupperclient.management.music.MusicPreparation;
import cn.pupperclient.management.music.MusicPreparation.Cancellation;
import cn.pupperclient.management.music.MusicPreparation.Key;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Deterministic single-flight and cancellation fixtures; no Minecraft, remote requests or audio hardware. */
public final class MusicPrefetchChecks {
    private static int checks;
    private static final Key A = key("netease:11"), B = key("audius:22");

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        checks = 0;
        joinsAndRevalidation();
        adoptionSurvivesCandidateChanges();
        staleCompletionCannotRemoveReplacement();
        blockingCleanupDoesNotStrandAdoption();
        queuedSpeculationStaysBounded();
        qualityAndAccountIsolation();
        quietFailureAndRetry();
        cancellationResources();
        closeSuppressesCallbacks();
        rejectedExecutors();
        inlineCallbacksAreIndependent();
        subscriberHandoffKeepsTransfer();
        lastSubscriberCancelsTransfer();
        speculativeDemandOutlivesSubscriber();
        queuedSubscriberCanBeClosed();
        completedSubscriberCannotCancelReplacement();
        System.out.println("Music prefetch checks passed: " + checks
            + " assertions; single-flight adoption, cancellable subscriptions, permission revalidation, bounded speculation, account/quality isolation and shutdown.");
    }

    private static Key key(String track) { return new Key(track, "standard", "a".repeat(64)); }

    private static void joinsAndRevalidation() throws Exception {
        try (Fixture fixture = new Fixture(1)) {
            Blocked operation = new Blocked("prefetched");
            List<String> results = new ArrayList<>();
            AtomicInteger unexpected = new AtomicInteger(), failures = new AtomicInteger();
            fixture.preparation.prefetch(A, operation);
            waitFor(operation.started);
            for (int i = 0; i < 20; i++) fixture.preparation.prefetch(A, _ -> { unexpected.incrementAndGet(); return "duplicate"; });
            for (int i = 0; i < 2; i++) fixture.preparation.prepare(A,
                _ -> { unexpected.incrementAndGet(); return "duplicate"; }, results::add, _ -> failures.incrementAndGet());
            operation.release.countDown();
            await(() -> fixture.callbacks.size() == 2);
            fixture.callbacks.drain();
            require(results.equals(List.of("prefetched", "prefetched")) && unexpected.get() == 0,
                "A foreground request did not adopt the matching in-flight prefetch");
            require(operation.calls.get() == 1 && failures.get() == 0, "Duplicate notification caused work or busy failure");
            fixture.preparation.prepare(A, _ -> { unexpected.incrementAndGet(); return "revalidated"; }, results::add,
                _ -> failures.incrementAndGet());
            await(() -> fixture.callbacks.size() == 1);
            fixture.callbacks.drain();
            require(results.getLast().equals("revalidated") && unexpected.get() == 1,
                "Completed in-memory audio bypassed provider permission revalidation");
            fixture.preparation.prefetch(A, _ -> { unexpected.incrementAndGet(); return "duplicate"; });
            fixture.barrier();
            require(unexpected.get() == 1, "Completed prefetch notification was not deduplicated");
            fixture.preparation.cancelPrefetch();
            fixture.preparation.prefetch(A, _ -> { unexpected.incrementAndGet(); return "new candidate"; });
            fixture.barrier();
            require(unexpected.get() == 2, "Clearing the candidate did not reset notification deduplication");
        }
    }

    private static void adoptionSurvivesCandidateChanges() throws Exception {
        try (Fixture fixture = new Fixture(2)) {
            Blocked adopted = new Blocked("adopted"), speculative = new Blocked("abandoned");
            AtomicInteger successes = new AtomicInteger(), failures = new AtomicInteger();
            fixture.preparation.prefetch(A, adopted);
            waitFor(adopted.started);
            fixture.preparation.prepare(A, _ -> "wrong", _ -> successes.incrementAndGet(), _ -> failures.incrementAndGet());
            fixture.preparation.prefetch(B, speculative);
            waitFor(speculative.started);
            require(adopted.disconnections.get() == 0 && !adopted.cancellation.get().isCancelled(),
                "Choosing another candidate cancelled foreground-adopted preparation");
            fixture.preparation.cancelPrefetch();
            waitFor(speculative.finished);
            require(speculative.disconnections.get() == 1 && speculative.cancellation.get().isCancelled(),
                "Cancelling speculation did not disconnect blocked I/O");
            require(adopted.disconnections.get() == 0, "Cancelling speculation touched an adopted request");
            adopted.release.countDown();
            await(() -> fixture.callbacks.size() == 1);
            fixture.callbacks.drain();
            require(successes.get() == 1 && failures.get() == 0, "Adopted preparation lost its foreground callback");
        }
    }

    private static void staleCompletionCannotRemoveReplacement() throws Exception {
        try (Fixture fixture = new Fixture(3)) {
            Blocked stale = new Blocked("stale", false), next = new Blocked("next"), replacement = new Blocked("replacement");
            List<String> results = new ArrayList<>();
            AtomicInteger unexpected = new AtomicInteger(), failures = new AtomicInteger();
            fixture.preparation.prefetch(A, stale);
            waitFor(stale.started);
            fixture.preparation.prefetch(B, next);
            waitFor(next.started);
            fixture.preparation.prepare(A, replacement, results::add, _ -> failures.incrementAndGet());
            waitFor(replacement.started);
            stale.release.countDown();
            waitFor(stale.finished);
            fixture.barrier(); // The other two workers remain blocked; this runs after stale completion.
            fixture.preparation.prepare(A, _ -> { unexpected.incrementAndGet(); return "duplicate"; }, results::add,
                _ -> failures.incrementAndGet());
            replacement.release.countDown();
            await(() -> fixture.callbacks.size() == 2);
            fixture.callbacks.drain();
            require(results.equals(List.of("replacement", "replacement")) && unexpected.get() == 0,
                "A cancelled request removed or delivered into its same-key replacement");
            require(stale.disconnections.get() == 1 && failures.get() == 0,
                "Stale speculative work surfaced a foreground failure");
        }
    }

    private static void queuedSpeculationStaysBounded() throws Exception {
        try (Fixture fixture = new Fixture(1)) {
            Blocked occupying = new Blocked("occupying");
            AtomicInteger adopted = new AtomicInteger(), abandoned = new AtomicInteger();
            List<String> results = new ArrayList<>();
            fixture.preparation.prepare(key("local:foreground"), occupying, results::add, MusicPrefetchChecks::unexpected);
            waitFor(occupying.started);
            fixture.preparation.prefetch(A, _ -> { adopted.incrementAndGet(); return "adopted"; });
            fixture.preparation.prepare(A, _ -> "wrong", results::add, MusicPrefetchChecks::unexpected);
            for (int i = 0; i < 30; i++) fixture.preparation.prefetch(key("audius:" + i),
                _ -> { abandoned.incrementAndGet(); return "unselected"; });
            require(((ThreadPoolExecutor) fixture.worker).getQueue().size() == 2,
                "Cancelled speculative tasks accumulated in the worker queue");
            fixture.preparation.cancelPrefetch();
            require(((ThreadPoolExecutor) fixture.worker).getQueue().size() == 1,
                "Cancelling speculation removed adopted work or retained abandoned work");
            occupying.release.countDown();
            await(() -> fixture.callbacks.size() == 2);
            fixture.callbacks.drain();
            require(adopted.get() == 1 && abandoned.get() == 0 && results.equals(List.of("occupying", "adopted")),
                "Queued foreground adoption or speculative cancellation changed the selected songs");
        }
    }

    private static void blockingCleanupDoesNotStrandAdoption() throws Exception {
        try (Fixture fixture = new Fixture(2)) {
            CountDownLatch oldStarted = new CountDownLatch(1), resourceRelease = new CountDownLatch(1);
            CountDownLatch hookStarted = new CountDownLatch(1), hookRelease = new CountDownLatch(1);
            Blocked candidate = new Blocked("adopted during cleanup");
            AtomicInteger successes = new AtomicInteger(), unexpected = new AtomicInteger();
            fixture.preparation.prefetch(A, cancellation -> {
                try (var registration = cancellation.onCancel(() -> {
                    hookStarted.countDown();
                    try { awaitUninterrupted(hookRelease); }
                    catch (IOException failure) { throw new IllegalStateException(failure); }
                    resourceRelease.countDown();
                })) {
                    oldStarted.countDown(); awaitUninterrupted(resourceRelease); cancellation.check(); return "old";
                }
            });
            waitFor(oldStarted);
            Thread selector = new Thread(() -> fixture.preparation.prefetch(B, candidate), "prefetch-candidate-fixture");
            selector.start();
            try {
                waitFor(hookStarted);
                fixture.preparation.prepare(B, _ -> { unexpected.incrementAndGet(); return "wrong"; },
                    _ -> successes.incrementAndGet(), MusicPrefetchChecks::unexpected);
                waitFor(candidate.started);
                candidate.release.countDown();
                await(() -> fixture.callbacks.size() == 1);
                fixture.callbacks.drain();
                require(successes.get() == 1 && unexpected.get() == 0,
                    "Blocking old I/O cleanup stranded a newly adopted foreground request");
            } finally {
                hookRelease.countDown(); resourceRelease.countDown(); candidate.release.countDown();
                selector.join(3_000);
            }
            require(!selector.isAlive(), "Candidate replacement leaked its cancellation caller");
        }
    }

    private static void qualityAndAccountIsolation() throws Exception {
        try (Fixture fixture = new Fixture(3)) {
            Key quality = new Key(A.trackIdentity(), "lossless", A.accessContext());
            Key account = new Key(A.trackIdentity(), A.quality(), "b".repeat(64));
            CountDownLatch started = new CountDownLatch(3), release = new CountDownLatch(1);
            AtomicInteger successes = new AtomicInteger();
            for (Key key : List.of(A, quality, account)) fixture.preparation.prepare(key, cancellation -> {
                started.countDown(); waitFor(release); cancellation.check(); return key.quality();
            }, _ -> successes.incrementAndGet(), MusicPrefetchChecks::unexpected);
            waitFor(started);
            require(!A.equals(quality) && !A.equals(account), "Quality or access context lost its cache identity");
            require(!A.toString().contains(A.accessContext()) && A.toString().contains("<redacted>"),
                "Key diagnostic output exposed a credential fingerprint");
            release.countDown();
            await(() -> fixture.callbacks.size() == 3);
            fixture.callbacks.drain();
            require(successes.get() == 3, "Different quality/account requests were incorrectly joined");
        }
    }

    private static void quietFailureAndRetry() throws Exception {
        try (Fixture fixture = new Fixture(1)) {
            AtomicInteger attempts = new AtomicInteger(), successes = new AtomicInteger();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            MusicPreparation.Operation<String> unavailable = _ -> { attempts.incrementAndGet(); throw new IOException("unavailable"); };
            fixture.preparation.prefetch(A, unavailable);
            fixture.barrier();
            fixture.preparation.prefetch(A, unavailable);
            fixture.barrier();
            require(attempts.get() == 2 && fixture.callbacks.size() == 0, "Speculative failure was noisy or poisoned a retry");
            fixture.preparation.prepare(A, _ -> { attempts.incrementAndGet(); return "retry"; }, _ -> successes.incrementAndGet(), failure::set);
            await(() -> fixture.callbacks.size() == 1);
            fixture.callbacks.drain();
            require(attempts.get() == 3 && successes.get() == 1 && failure.get() == null,
                "Failed speculation poisoned a later foreground request");
            fixture.preparation.prepare(A, unavailable, _ -> successes.incrementAndGet(), failure::set);
            await(() -> fixture.callbacks.size() == 1);
            fixture.callbacks.drain();
            require(failure.get() instanceof IOException && successes.get() == 1, "Foreground failure was not delivered");
        }
    }

    private static void cancellationResources() throws Exception {
        try (Fixture fixture = new Fixture(1)) {
            AtomicReference<Cancellation> token = new AtomicReference<>();
            AtomicInteger closed = new AtomicInteger(), live = new AtomicInteger(), late = new AtomicInteger();
            CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
            fixture.preparation.prefetch(A, cancellation -> {
                token.set(cancellation);
                try (var unregistered = cancellation.onCancel(closed::incrementAndGet);
                     var throwing = cancellation.onCancel(() -> { throw new IllegalStateException("fixture disconnect"); });
                     var registered = cancellation.onCancel(() -> { live.incrementAndGet(); release.countDown(); })) {
                    unregistered.close(); started.countDown(); awaitUninterrupted(release); cancellation.check(); return "wrong";
                } finally { finished.countDown(); }
            });
            waitFor(started);
            fixture.preparation.cancelPrefetch();
            fixture.preparation.cancelPrefetch();
            waitFor(finished);
            require(closed.get() == 0 && live.get() == 1, "Registration removal or one-shot disconnect semantics failed");
            try (var registration = token.get().onCancel(late::incrementAndGet)) {
                require(late.get() == 1, "Resource registered after cancellation remained connected");
            }
            require(late.get() == 1, "Late registration ran more than once");
            try { token.get().check(); throw new AssertionError("Cancellation check did not fail"); }
            catch (InterruptedIOException expected) { checks++; }
            require(fixture.callbacks.size() == 0, "Cancellation produced a speculative callback");
        }
        Thread.currentThread().interrupt();
        try {
            try { new Cancellation().check(); throw new AssertionError("Interrupt was ignored"); }
            catch (InterruptedIOException expected) { checks++; }
        } finally { Thread.interrupted(); }
    }

    private static void closeSuppressesCallbacks() throws Exception {
        try (Fixture fixture = new Fixture(2)) {
            AtomicInteger successes = new AtomicInteger(), failures = new AtomicInteger(), afterClose = new AtomicInteger();
            fixture.preparation.prepare(A, _ -> "queued", _ -> successes.incrementAndGet(), _ -> failures.incrementAndGet());
            await(() -> fixture.callbacks.size() == 1);
            Blocked speculative = new Blocked("speculative"), foreground = new Blocked("foreground");
            fixture.preparation.prefetch(B, speculative);
            fixture.preparation.prepare(key("netease:33"), foreground, _ -> successes.incrementAndGet(), _ -> failures.incrementAndGet());
            waitFor(speculative.started); waitFor(foreground.started);
            fixture.preparation.close(); fixture.preparation.close();
            waitFor(speculative.finished); waitFor(foreground.finished);
            fixture.callbacks.drain();
            fixture.preparation.prepare(A, _ -> { afterClose.incrementAndGet(); return "wrong"; },
                _ -> successes.incrementAndGet(), _ -> failures.incrementAndGet());
            fixture.preparation.prefetch(B, _ -> { afterClose.incrementAndGet(); return "wrong"; });
            require(successes.get() == 0 && failures.get() == 0 && afterClose.get() == 0,
                "A queued or late callback escaped shutdown");
            require(speculative.disconnections.get() == 1 && foreground.disconnections.get() == 1,
                "Shutdown did not disconnect both speculative and adopted I/O");
            require(!fixture.worker.isShutdown(), "Coordinator shut down a caller-owned executor");
        }
    }

    private static void rejectedExecutors() throws Exception {
        ExecutorService actual = Executors.newSingleThreadExecutor();
        RejectOnceExecutor worker = new RejectOnceExecutor(actual);
        QueuedCallbacks callbacks = new QueuedCallbacks();
        try (MusicPreparation<String> preparation = new MusicPreparation<>(worker, callbacks)) {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicInteger operations = new AtomicInteger(), successes = new AtomicInteger();
            preparation.prepare(A, _ -> { operations.incrementAndGet(); return "wrong"; }, _ -> successes.incrementAndGet(), failure::set);
            require(callbacks.size() == 1 && operations.get() == 0, "Rejected worker lost the foreground failure");
            callbacks.drain();
            require(failure.get() instanceof RejectedExecutionException, "Rejected worker failure changed type");
            preparation.prepare(A, _ -> { operations.incrementAndGet(); return "retry"; }, _ -> successes.incrementAndGet(), failure::set);
            await(() -> callbacks.size() == 1);
            callbacks.drain();
            require(operations.get() == 1 && successes.get() == 1, "Rejected worker left a poisoned in-flight entry");
            worker.reject.set(true);
            preparation.prefetch(B, _ -> { operations.incrementAndGet(); return "wrong"; });
            require(callbacks.size() == 0, "Rejected speculation emitted a callback");
            preparation.prefetch(B, _ -> { operations.incrementAndGet(); return "retry"; });
            actual.submit(() -> { }).get(2, TimeUnit.SECONDS);
            require(operations.get() == 2, "Rejected speculation poisoned notification deduplication");
        } finally { stop(actual); }
        ExecutorService callbackWorker = Executors.newSingleThreadExecutor();
        AtomicInteger successes = new AtomicInteger(), failures = new AtomicInteger();
        try (MusicPreparation<String> preparation = new MusicPreparation<>(callbackWorker,
                _ -> { throw new RejectedExecutionException("fixture callback executor"); })) {
            preparation.prepare(A, _ -> "result", _ -> successes.incrementAndGet(), failure -> {
                if (failure instanceof RejectedExecutionException) failures.incrementAndGet();
            });
            await(() -> failures.get() == 1);
            require(successes.get() == 0, "Rejected callback executor ran success on the wrong thread");
        } finally { stop(callbackWorker); }
    }

    private static void inlineCallbacksAreIndependent() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (MusicPreparation<String> preparation = new MusicPreparation<>(worker, Runnable::run)) {
            AtomicInteger second = new AtomicInteger(), failures = new AtomicInteger();
            for (boolean rejected : List.of(false, true)) {
                Blocked operation = new Blocked("result");
                preparation.prepare(A, operation, _ -> {
                    if (rejected) throw new RejectedExecutionException("consumer exception");
                    throw new IllegalStateException("consumer exception");
                }, _ -> failures.incrementAndGet());
                waitFor(operation.started);
                preparation.prepare(A, _ -> "wrong", _ -> second.incrementAndGet(), _ -> failures.incrementAndGet());
                operation.release.countDown();
                int expected = rejected ? 2 : 1;
                await(() -> second.get() == expected);
                require(failures.get() == 0, "Consumer exception was misclassified as executor rejection");
            }
            require(second.get() == 2, "One inline callback suppressed the other foreground subscriber");
        } finally { stop(worker); }
    }

    private static void subscriberHandoffKeepsTransfer() throws Exception {
        try (Fixture fixture = new Fixture(1)) {
            Blocked operation = new Blocked("same transfer");
            AtomicInteger previous = new AtomicInteger(), selected = new AtomicInteger(), duplicate = new AtomicInteger();
            var old = fixture.preparation.prepare(A, operation, _ -> previous.incrementAndGet(), MusicPrefetchChecks::unexpected);
            waitFor(operation.started);
            var current = fixture.preparation.prepare(A, _ -> { duplicate.incrementAndGet(); return "wrong"; },
                _ -> selected.incrementAndGet(), MusicPrefetchChecks::unexpected);
            old.close(); old.close();
            require(!operation.cancellation.get().isCancelled() && operation.disconnections.get() == 0,
                "Closing one subscriber interrupted another subscriber's preparation");
            operation.release.countDown();
            await(() -> fixture.callbacks.size() == 1);
            fixture.callbacks.drain();
            require(previous.get() == 0 && selected.get() == 1 && duplicate.get() == 0 && operation.calls.get() == 1,
                "Same-key subscribe-before-close restarted the transfer or delivered a stale callback");
            current.close();
            require(operation.disconnections.get() == 0, "Closing a completed subscription cancelled its successful operation");
        }
    }

    private static void lastSubscriberCancelsTransfer() throws Exception {
        try (Fixture fixture = new Fixture(1)) {
            Blocked operation = new Blocked("abandoned foreground");
            AtomicInteger callbacks = new AtomicInteger();
            var subscription = fixture.preparation.prepare(A, operation, _ -> callbacks.incrementAndGet(), _ -> callbacks.incrementAndGet());
            waitFor(operation.started);
            subscription.close(); subscription.close();
            waitFor(operation.finished);
            fixture.barrier();
            require(operation.cancellation.get().isCancelled() && operation.disconnections.get() == 1,
                "The final foreground subscriber did not cancel blocked I/O");
            require(fixture.callbacks.size() == 0 && callbacks.get() == 0, "Abandoned foreground work emitted a callback");
            fixture.preparation.prepare(A, _ -> "fresh", _ -> callbacks.incrementAndGet(), MusicPrefetchChecks::unexpected);
            await(() -> fixture.callbacks.size() == 1);
            fixture.callbacks.drain();
            require(callbacks.get() == 1, "Cancelled foreground work poisoned its next same-key request");
        }
    }

    private static void speculativeDemandOutlivesSubscriber() throws Exception {
        for (boolean speculativeFirst : List.of(false, true)) {
            try (Fixture fixture = new Fixture(1)) {
                Blocked operation = new Blocked("still requested speculatively");
                AtomicInteger callbacks = new AtomicInteger();
                if (speculativeFirst) fixture.preparation.prefetch(A, operation);
                var subscription = fixture.preparation.prepare(A, operation, _ -> callbacks.incrementAndGet(), _ -> callbacks.incrementAndGet());
                waitFor(operation.started);
                if (!speculativeFirst) fixture.preparation.prefetch(A, _ -> "wrong");
                subscription.close();
                require(!operation.cancellation.get().isCancelled() && operation.disconnections.get() == 0,
                    "Closing the last foreground subscriber discarded an existing speculative demand");
                fixture.preparation.cancelPrefetch();
                waitFor(operation.finished);
                fixture.barrier();
                require(operation.disconnections.get() == 1 && fixture.callbacks.size() == 0 && callbacks.get() == 0,
                    "Removing the final speculative demand did not cancel the unsubscribed request");
            }
        }
    }

    private static void queuedSubscriberCanBeClosed() throws Exception {
        for (boolean failed : List.of(false, true)) {
            try (Fixture fixture = new Fixture(1)) {
                AtomicInteger callbacks = new AtomicInteger();
                var subscription = fixture.preparation.prepare(A, _ -> {
                    if (failed) throw new IOException("queued failure");
                    return "queued success";
                }, _ -> callbacks.incrementAndGet(), _ -> callbacks.incrementAndGet());
                await(() -> fixture.callbacks.size() == 1);
                subscription.close();
                fixture.callbacks.drain();
                require(callbacks.get() == 0, "Closing a completed subscription did not suppress its queued callback");
            }
        }
    }

    private static void completedSubscriberCannotCancelReplacement() throws Exception {
        try (Fixture fixture = new Fixture(1)) {
            AtomicInteger stale = new AtomicInteger(), current = new AtomicInteger(), duplicates = new AtomicInteger();
            var old = fixture.preparation.prepare(A, _ -> "completed", _ -> stale.incrementAndGet(), MusicPrefetchChecks::unexpected);
            await(() -> fixture.callbacks.size() == 1);
            Blocked replacement = new Blocked("replacement");
            var selected = fixture.preparation.prepare(A, replacement, _ -> current.incrementAndGet(), MusicPrefetchChecks::unexpected);
            waitFor(replacement.started);
            old.close();
            var joined = fixture.preparation.prepare(A, _ -> { duplicates.incrementAndGet(); return "wrong"; },
                _ -> current.incrementAndGet(), MusicPrefetchChecks::unexpected);
            require(!replacement.cancellation.get().isCancelled(), "An old queued callback handle cancelled a new same-key request");
            replacement.release.countDown();
            await(() -> fixture.callbacks.size() == 3);
            selected.close();
            fixture.callbacks.drain();
            require(stale.get() == 0 && current.get() == 1 && duplicates.get() == 0 && replacement.calls.get() == 1,
                "Closing queued old/current subscriptions affected the remaining joined subscriber");
            joined.close();
        }
    }

    private static final class Blocked implements MusicPreparation.Operation<String> {
        private final String value;
        private final boolean releaseOnCancel;
        private final AtomicInteger calls = new AtomicInteger(), disconnections = new AtomicInteger();
        private final AtomicReference<Cancellation> cancellation = new AtomicReference<>();
        private final CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
        private Blocked(String value) { this(value, true); }
        private Blocked(String value, boolean releaseOnCancel) { this.value = value; this.releaseOnCancel = releaseOnCancel; }
        @Override public String run(Cancellation token) throws Exception {
            calls.incrementAndGet(); cancellation.set(token);
            try (var registration = token.onCancel(() -> {
                disconnections.incrementAndGet(); if (releaseOnCancel) release.countDown();
            })) {
                started.countDown(); awaitUninterrupted(release); token.check(); return value;
            } finally { finished.countDown(); }
        }
    }

    private static final class QueuedCallbacks implements Executor {
        private final Queue<Runnable> pending = new ArrayDeque<>();
        @Override public synchronized void execute(Runnable command) { pending.add(command); }
        private synchronized int size() { return pending.size(); }
        private void drain() {
            while (true) {
                Runnable callback;
                synchronized (this) { callback = pending.poll(); }
                if (callback == null) return;
                callback.run();
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final ExecutorService worker;
        private final QueuedCallbacks callbacks = new QueuedCallbacks();
        private final MusicPreparation<String> preparation;
        private Fixture(int threads) {
            worker = Executors.newFixedThreadPool(threads);
            preparation = new MusicPreparation<>(worker, callbacks);
        }
        private void barrier() throws Exception { worker.submit(() -> { }).get(2, TimeUnit.SECONDS); }
        @Override public void close() throws Exception { preparation.close(); stop(worker); }
    }

    private static final class RejectOnceExecutor extends AbstractExecutorService {
        private final ExecutorService actual;
        private final AtomicBoolean reject = new AtomicBoolean(true);
        private RejectOnceExecutor(ExecutorService actual) { this.actual = actual; }
        @Override public void execute(Runnable command) {
            if (reject.getAndSet(false)) throw new RejectedExecutionException("fixture worker");
            actual.execute(command);
        }
        @Override public void shutdown() { actual.shutdown(); }
        @Override public List<Runnable> shutdownNow() { return actual.shutdownNow(); }
        @Override public boolean isShutdown() { return actual.isShutdown(); }
        @Override public boolean isTerminated() { return actual.isTerminated(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException { return actual.awaitTermination(timeout, unit); }
    }

    private static void awaitUninterrupted(CountDownLatch latch) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (true) {
            try {
                if (latch.await(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) return;
                throw new IOException("Blocked fixture timed out");
            } catch (InterruptedException ignored) { /* A fake socket closes only when its disconnector is called. */ }
        }
    }

    private static void waitFor(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError("Concurrent fixture timed out");
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Concurrent fixture timed out");
            Thread.sleep(2);
        }
    }

    private static void stop(ExecutorService worker) throws InterruptedException {
        worker.shutdownNow();
        if (!worker.awaitTermination(4, TimeUnit.SECONDS)) throw new AssertionError("Fixture leaked a worker");
    }

    private static void unexpected(Throwable failure) { throw new AssertionError("Unexpected preparation failure", failure); }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
