package cn.pupperclient.management.music;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** UI-independent login workflows. Replaced logins, logout and shutdown invalidate pending replies. */
public final class MusicLoginService implements AutoCloseable {
    public enum QrState { WAITING, SCANNED }
    @FunctionalInterface public interface Scheduler { void schedule(Runnable task, long delayMillis); }
    @FunctionalInterface private interface Operation<T> { T run() throws Exception; }
    private final NeteaseMusicApi api;
    private final MusicAccountStore store;
    private final Executor worker, callbacks;
    private final Scheduler scheduler;
    private long generation;
    private boolean closed;

    public MusicLoginService(NeteaseMusicApi api, MusicAccountStore store, Executor worker,
            Executor callbacks, Scheduler scheduler) {
        this.api = api; this.store = store; this.worker = worker;
        this.callbacks = callbacks; this.scheduler = scheduler;
    }
    public MusicAccount account() { return store.snapshot(); }

    public void sendCaptcha(String phone, Consumer<Void> success, Consumer<MusicError> failure) {
        submit(version(), () -> { api.sendCaptcha(phone); return null; }, success, failure);
    }
    public void phoneLogin(String phone, String captcha, Consumer<MusicAccount> success, Consumer<MusicError> failure) {
        long token = begin();
        submit(token, () -> commit(token, api.phoneLogin(phone, captcha)), success, failure);
    }
    public void check(Consumer<MusicAccount> success, Consumer<MusicError> failure) {
        long token = version(); MusicAccount previous = account();
        submit(token, () -> previous.authenticated()
            ? commitStatus(token, previous, api.loginStatus(previous.cookie(), previous.phone())) : previous, success, failure);
    }
    public void refresh(Consumer<MusicAccount> success, Consumer<MusicError> failure) {
        long token = begin(); MusicAccount previous = account();
        submit(token, () -> {
            if (!previous.authenticated()) throw new MusicError("music.error.login");
            String cookie = api.refreshLogin(previous.cookie());
            return commit(token, new MusicAccount(cookie, previous.userId(), previous.nickname(), previous.phone()));
        }, success, failure);
    }
    public void logout(Consumer<Void> success, Consumer<MusicError> failure) {
        long token = begin(); MusicAccount previous = account();
        submit(token, () -> {
            // Local logout succeeds even when the remote session endpoint is unavailable.
            commit(token, MusicAccount.GUEST);
            if (current(token) && previous.authenticated()) try { api.logout(previous.cookie()); }
            catch (MusicError unavailable) { /* Local session is already removed. */ }
            return null;
        }, success, failure);
    }
    public void qrLogin(Consumer<NeteaseMusicApi.QrCode> ready, Consumer<QrState> state,
            Consumer<MusicAccount> success, Consumer<MusicError> failure) {
        long token = begin();
        submit(token, api::createLoginQr, code -> {
            ready.accept(code);
            if (!current(token)) return;
            state.accept(QrState.WAITING);
            poll(token, code.key(), 0, false, state, success, failure);
        }, failure);
    }
    private void poll(long token, String key, int attempt, boolean scanned, Consumer<QrState> state,
            Consumer<MusicAccount> success, Consumer<MusicError> failure) {
        if (!current(token)) return;
        scheduler.schedule(() -> submit(token, () -> api.checkLoginQr(key), result -> {
            if (result.code() == 803) {
                submit(token, () -> commit(token, api.loginAccount(result.cookie(), "")), success, failure);
            } else if (result.code() == 800) failure.accept(new MusicError("music.login.error.qrexpired"));
            else {
                boolean nowScanned = scanned || result.code() == 802;
                if (nowScanned && !scanned) state.accept(QrState.SCANNED);
                if (attempt + 1 >= 60) failure.accept(new MusicError("music.login.error.qrtimeout"));
                else poll(token, key, attempt + 1, nowScanned, state, success, failure);
            }
        }, failure), 3_000);
    }

    private synchronized long begin() { return ++generation; }
    private synchronized long version() { return generation; }
    private synchronized boolean current(long token) { return !closed && token == generation; }
    private synchronized MusicAccount commit(long token, MusicAccount next) throws IOException {
        if (current(token)) store.save(next);
        return next;
    }
    private synchronized MusicAccount commitStatus(long token, MusicAccount previous, MusicAccount next) throws IOException {
        if (current(token) && store.snapshot() == previous) store.save(next);
        return store.snapshot();
    }
    private <T> void submit(long token, Operation<T> operation, Consumer<T> success, Consumer<MusicError> failure) {
        worker.execute(() -> {
            if (!current(token)) return;
            T result;
            try { result = operation.run(); }
            catch (Exception error) {
                MusicError translated = error instanceof MusicError musicError ? musicError
                    : new MusicError(error instanceof IOException ? "music.error.file" : "music.error.network");
                callbacks.execute(() -> { if (current(token)) failure.accept(translated); });
                return;
            }
            callbacks.execute(() -> { if (current(token)) success.accept(result); });
        });
    }
    @Override public synchronized void close() { closed = true; generation++; }
}
