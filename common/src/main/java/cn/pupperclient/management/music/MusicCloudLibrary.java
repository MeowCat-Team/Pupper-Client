package cn.pupperclient.management.music;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Read-only account playlists. No local library writes and no account data shared between sessions. */
public final class MusicCloudLibrary implements AutoCloseable {
    public record Page(List<MusicCollection> created, List<MusicCollection> subscribed, int offset,
            int nextOffset, boolean more) {
        public Page { created = List.copyOf(created); subscribed = List.copyOf(subscribed); }
    }
    private record Context(MusicAccount account, long sessionVersion, long endpointRevision) { }
    private final NeteaseMusicApi api;
    private final MusicLoginService login;
    private final Executor worker, callbacks;
    private final Set<Request> pending = ConcurrentHashMap.newKeySet();
    private final MusicPreparation.Registration sessionListener;
    private volatile boolean closed;

    public MusicCloudLibrary(NeteaseMusicApi api, MusicLoginService login, Executor worker, Executor callbacks) {
        this.api = api; this.login = login; this.worker = worker; this.callbacks = callbacks;
        sessionListener = login.onSessionChange(this::invalidate);
    }
    private boolean current(Context context) {
        return !closed && api.configuration().snapshot().revision() == context.endpointRevision()
            && login.sessionVersion() == context.sessionVersion() && login.account().equals(context.account());
    }
    public MusicPreparation.Registration playlists(int limit, int offset, Consumer<Page> success,
            Consumer<MusicError> failure) {
        var context = new Context(login.account(), login.sessionVersion(), api.configuration().snapshot().revision());
        var request = new Request(); pending.add(request);
        if (closed) { request.close(); return request; }
        int pageSize = Math.clamp(limit, 1, 50), pageOffset = Math.max(0, offset);
        Runnable operation = () -> {
            if (!request.active.get() || !current(context)) { request.close(); return; }
            Page page = null; MusicError error = null;
            try {
                if (!context.account().authenticated()) throw new MusicError("music.error.login");
                var result = api.userPlaylists(context.account(), pageSize, pageOffset, request.cancellation);
                page = new Page(result.created(), result.subscribed(), result.offset(), result.nextOffset(), result.more());
            } catch (MusicError unavailable) {
                error = unavailable;
                if (unavailable.key().equals("music.error.login") && context.account().authenticated() && current(context)) {
                    request.expiring = true;
                    if (login.expire(context.account())) request.expiredVersion = login.sessionVersion();
                    request.expiring = false;
                }
            } catch (RuntimeException unavailable) { error = new MusicError("music.error.network"); }
            Page result = page; MusicError unavailable = error;
            try { callbacks.execute(() -> {
                try {
                    boolean valid = request.expiredVersion >= 0 ? !closed
                        && api.configuration().snapshot().revision() == context.endpointRevision()
                        && login.sessionVersion() == request.expiredVersion && !login.account().authenticated() : current(context);
                    if (valid && request.active.compareAndSet(true, false)) {
                        if (unavailable == null) success.accept(result); else failure.accept(unavailable);
                    }
                } finally { request.close(); }
            }); } catch (RuntimeException rejected) { request.close(); }
        };
        try { worker.execute(operation); }
        catch (RuntimeException rejected) {
            try { callbacks.execute(() -> { try {
                if (current(context) && request.active.compareAndSet(true, false)) failure.accept(new MusicError("music.error.network"));
            } finally { request.close(); } }); } catch (RuntimeException callbackRejected) { request.close(); }
        }
        return request;
    }
    /** Clears outstanding pages on logout, login replacement, expiry and endpoint changes. */
    public void invalidate() { for (Request request : pending) if (!request.expiring) request.close(); }
    @Override public void close() { closed = true; sessionListener.close(); invalidate(); }
    private final class Request implements MusicPreparation.Registration {
        final AtomicBoolean active = new AtomicBoolean(true);
        final MusicPreparation.Cancellation cancellation = new MusicPreparation.Cancellation();
        volatile boolean expiring;
        volatile long expiredVersion = -1;
        @Override public void close() { active.set(false); pending.remove(this); cancellation.cancel(); }
    }
}
