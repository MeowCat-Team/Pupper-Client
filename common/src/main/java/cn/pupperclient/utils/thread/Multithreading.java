package cn.pupperclient.utils.thread;

import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class Multithreading {
    private static final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private static final ExecutorService cachedThreadPool = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder()
            .setNameFormat("Pupper Client-%d")
            .setThreadFactory(Thread.ofVirtual().factory())
            .build()
    );

    private static final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(
        Runtime.getRuntime().availableProcessors(),
        Thread.ofVirtual().factory()
    );

    public static void runAsync(Runnable runnable) {
        submit(runnable);
    }

    public static void runMainThread(Runnable runnable) {
        Minecraft.getInstance().execute(runnable);
    }

    public static void submit(Runnable runnable) {
        virtualThreadExecutor.submit(runnable);
    }

    public static void schedule(Runnable runnable, long delay, TimeUnit timeUnit) {
        submitScheduled(runnable, delay, timeUnit);
    }

    public static void submitScheduled(Runnable runnable, long delay, TimeUnit timeUnit) {
        scheduledExecutor.schedule(runnable, delay, timeUnit);
    }

    public static CompletableFuture<Void> runAsyncFuture(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, virtualThreadExecutor);
    }

    public static void shutdown() {
        virtualThreadExecutor.shutdown();
        cachedThreadPool.shutdown();
        scheduledExecutor.shutdown();

        try {
            if (!virtualThreadExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                virtualThreadExecutor.shutdownNow();
            }
            if (!cachedThreadPool.awaitTermination(1, TimeUnit.SECONDS)) {
                cachedThreadPool.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
