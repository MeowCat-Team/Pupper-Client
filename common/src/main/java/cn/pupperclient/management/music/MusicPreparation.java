package cn.pupperclient.management.music;

import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Shares only in-flight preparation; completed audio must be revalidated by the provider. */
public final class MusicPreparation<T> implements AutoCloseable {
    /** accessContext is an opaque credential fingerprint, never a cookie or access token. */
    public record Key(String trackIdentity, String quality, String accessContext) {
        public Key {
            Objects.requireNonNull(trackIdentity, "trackIdentity");
            Objects.requireNonNull(quality, "quality");
            Objects.requireNonNull(accessContext, "accessContext");
        }

        @Override
        public String toString() {
            return "Key[trackIdentity=" + trackIdentity + ", quality=" + quality + ", accessContext=<redacted>]";
        }
    }

    @FunctionalInterface
    public interface Operation<T> {
        T run(Cancellation cancellation) throws Exception;
    }

    /** Cancels a foreground subscription, or unregisters a cancellation resource hook. */
    public interface Registration extends AutoCloseable {
        @Override void close();
    }

    public static final class Cancellation {
        private final Object lock = new Object();
        private final Set<Hook> hooks = new LinkedHashSet<>();
        private volatile boolean cancelled;
        private boolean finished;

        public Cancellation() { }

        public boolean isCancelled() {
            return cancelled;
        }

        public void check() throws InterruptedIOException {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Music preparation cancelled");
            }
        }

        /** A late registration is disconnected immediately if cancellation already happened. */
        public Registration onCancel(Runnable action) {
            Hook hook = new Hook(Objects.requireNonNull(action, "action"));
            boolean cancelNow;
            synchronized (lock) {
                cancelNow = cancelled;
                if (!cancelNow && !finished) hooks.add(hook);
                else if (finished && !cancelNow) hook.active.set(false);
            }
            if (cancelNow) hook.fire();
            return hook;
        }

        public void cancel() {
            List<Hook> pending;
            synchronized (lock) {
                if (cancelled) return;
                cancelled = true;
                pending = List.copyOf(hooks);
                hooks.clear();
            }
            // Resource callbacks can block, throw or re-enter the coordinator.
            for (Hook hook : pending) hook.fire();
        }

        private void finish() {
            synchronized (lock) {
                finished = true;
                for (Hook hook : hooks) hook.active.set(false);
                hooks.clear();
            }
        }

        private final class Hook implements Registration {
            private final Runnable action;
            private final AtomicBoolean active = new AtomicBoolean(true);

            private Hook(Runnable action) {
                this.action = action;
            }

            private void fire() {
                if (active.compareAndSet(true, false)) {
                    try { action.run(); }
                    catch (RuntimeException ignored) { /* Still cancel the remaining resources and worker. */ }
                }
            }

            @Override
            public void close() {
                active.set(false);
                synchronized (lock) { hooks.remove(this); }
            }
        }
    }

    private final Object lock = new Object();
    private final ExecutorService worker;
    private final Executor callbacks;
    private final Map<Key, Request> inFlight = new HashMap<>();
    private Request speculative;
    private Key lastPrefetchKey;
    private volatile boolean closed;

    /** Injected executors remain owned by the caller and are never shut down here. */
    public MusicPreparation(ExecutorService worker, Executor callbackExecutor) {
        this.worker = Objects.requireNonNull(worker, "worker");
        this.callbacks = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
    }

    /**
     * A foreground request adopts matching speculative work instead of failing as busy.
     * Closing its subscription suppresses its callbacks and cancels work no subscriber or prefetch still needs.
     */
    public Registration prepare(Key key, Operation<T> operation, Consumer<T> success, Consumer<Throwable> failure) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(success, "success");
        Objects.requireNonNull(failure, "failure");
        Request request;
        Subscriber subscriber;
        boolean start;
        synchronized (lock) {
            if (closed) return () -> { };
            request = inFlight.get(key);
            start = request == null;
            if (start) {
                request = new Request(key, operation);
                inFlight.put(key, request);
            }
            subscriber = new Subscriber(request, success, failure);
            request.subscribers.add(subscriber);
        }
        if (start) start(request);
        return subscriber;
    }

    /** Only the newest unadopted speculative request survives. Repeated notifications are quiet. */
    public void prefetch(Key key, Operation<T> operation) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");
        Request abandoned, request;
        boolean start;
        synchronized (lock) {
            if (closed || key.equals(lastPrefetchKey)) return;
            lastPrefetchKey = key;
            abandoned = detachSpeculative();
            request = inFlight.get(key);
            start = request == null;
            if (start) {
                request = new Request(key, operation);
                inFlight.put(key, request);
            }
            request.speculativeDemand = true;
            speculative = request;
        }
        if (start) start(request);
        // A disconnector may block. The new candidate must already be runnable
        // when a foreground caller adopts it during that resource cleanup.
        cancel(abandoned);
    }

    public void cancelPrefetch() {
        Request abandoned;
        synchronized (lock) {
            lastPrefetchKey = null;
            abandoned = detachSpeculative();
        }
        cancel(abandoned);
    }

    /** Called under lock before cancellation; another thread cannot adopt a detached request. */
    private Request detachSpeculative() {
        Request previous = speculative;
        speculative = null;
        if (previous == null) return null;
        previous.speculativeDemand = false;
        return detachUnneeded(previous);
    }

    /** The map identity check also protects a new request with the same key after completion. */
    private Request detachUnneeded(Request request) {
        if (request.speculativeDemand || request.hasSubscribers() || inFlight.get(request.key) != request) return null;
        inFlight.remove(request.key);
        return request;
    }

    private void start(Request request) {
        synchronized (lock) {
            if (closed || inFlight.get(request.key) != request) return;
        }
        try { worker.execute(request.task); }
        catch (RejectedExecutionException failure) { complete(request, null, failure); }
    }

    private void run(Request request) {
        T result = null;
        Throwable failure = null;
        try {
            request.cancellation.check();
            result = request.operation.run(request.cancellation);
            request.cancellation.check();
        } catch (Throwable thrown) {
            failure = thrown;
        } finally {
            request.cancellation.finish();
        }
        complete(request, result, failure);
    }

    private void complete(Request request, T result, Throwable failure) {
        List<Subscriber> subscribers;
        synchronized (lock) {
            if (inFlight.get(request.key) != request) return;
            inFlight.remove(request.key);
            if (speculative == request) speculative = null;
            request.speculativeDemand = false;
            if (failure != null && request.key.equals(lastPrefetchKey)) lastPrefetchKey = null;
            subscribers = List.copyOf(request.subscribers);
            request.subscribers.clear();
        }
        for (Subscriber subscriber : subscribers) dispatch(subscriber, result, failure);
    }

    private void dispatch(Subscriber subscriber, T result, Throwable failure) {
        try {
            callbacks.execute(() -> deliver(subscriber, result, failure));
        } catch (RejectedExecutionException rejected) {
            // No success may run on an unexpected thread. Report dispatch failure outside all locks.
            deliver(subscriber, null, rejected);
        }
    }

    private void deliver(Subscriber subscriber, T result, Throwable failure) {
        if (closed || subscriber.request.cancellation.isCancelled()) {
            subscriber.close();
            return;
        }
        // Claim each callback at execution time, so a closed queued subscriber stays silent.
        if (!subscriber.active.compareAndSet(true, false)) return;
        isolated(() -> {
            if (failure == null) subscriber.success.accept(result);
            else subscriber.failure.accept(failure);
        });
    }

    private void isolated(Runnable callback) {
        try { callback.run(); }
        catch (RuntimeException ignored) { /* One subscriber cannot suppress another subscriber's result. */ }
    }

    private void cancel(Request request) {
        if (request == null) return;
        request.cancellation.cancel();
        request.task.cancel(true);
        if (worker instanceof ThreadPoolExecutor pool) pool.remove(request.task);
    }

    @Override
    public void close() {
        List<Request> requests;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            speculative = null;
            lastPrefetchKey = null;
            requests = List.copyOf(inFlight.values());
            for (Request request : requests) {
                request.speculativeDemand = false;
                for (Subscriber subscriber : request.subscribers) subscriber.active.set(false);
                request.subscribers.clear();
            }
            inFlight.clear();
        }
        for (Request request : requests) cancel(request);
    }

    private final class Request {
        private final Key key;
        private final Operation<T> operation;
        private final Cancellation cancellation = new Cancellation();
        private final List<Subscriber> subscribers = new ArrayList<>();
        private final FutureTask<Void> task;
        private boolean speculativeDemand;

        private boolean hasSubscribers() {
            return subscribers.stream().anyMatch(subscriber -> subscriber.active.get());
        }

        private Request(Key key, Operation<T> operation) {
            this.key = key;
            this.operation = operation;
            this.task = new FutureTask<>(() -> { run(this); return null; });
        }
    }

    private final class Subscriber implements Registration {
        private final Request request;
        private final Consumer<T> success;
        private final Consumer<Throwable> failure;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Subscriber(Request request, Consumer<T> success, Consumer<Throwable> failure) {
            this.request = request;
            this.success = success;
            this.failure = failure;
        }

        @Override
        public void close() {
            if (!active.compareAndSet(true, false)) return;
            Request abandoned;
            synchronized (lock) {
                request.subscribers.remove(this);
                abandoned = detachUnneeded(request);
            }
            cancel(abandoned);
        }
    }
}
