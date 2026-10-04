package cn.pupperclient.utils.system;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser.WNDENUMPROC;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class JNAWindowChecker {
    private static final User32 USER32 = User32.INSTANCE;
    private static ScheduledExecutorService scheduler;

    private static final AtomicReference<WindowInfo> cachedWindowInfo =
        new AtomicReference<>(new WindowInfo("No Song", System.currentTimeMillis()));

    private static final String[] TARGET_CLASS_NAMES = {
        "OrpheusBrowserHost",
        "CloudMusic"
    };

    public record WindowInfo(String title, long timestamp) {}

    public static void startBackgroundMonitoring() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().factory()
        );

        scheduler.scheduleAtFixedRate(() -> {
            try {
                String title = findCloudMusicWindowTitle();
                cachedWindowInfo.set(new WindowInfo(title, System.currentTimeMillis()));
            } catch (Exception e) {
                // 忽略异常，继续监控
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    public static void stopBackgroundMonitoring() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
    }

    public static String getCurrentWindowTitle() {
        return cachedWindowInfo.get().title();
    }

    private static String findCloudMusicWindowTitle() {
        final List<String> windowTitles = new ArrayList<>();

        WNDENUMPROC enumProc = (hwnd, arg) -> {
            if (USER32.IsWindowVisible(hwnd)) {
                char[] windowText = new char[1024];
                USER32.GetWindowText(hwnd, windowText, 1024);
                String title = Native.toString(windowText);

                char[] className = new char[256];
                USER32.GetClassName(hwnd, className, 256);
                String classNameStr = Native.toString(className);

                if (isCloudMusicWindow(title, classNameStr)) {
                    windowTitles.add(title);
                }
            }
            return true;
        };

        USER32.EnumWindows(enumProc, null);

        return windowTitles.isEmpty() ? "No Song" : windowTitles.getFirst();
    }

    private static boolean isCloudMusicWindow(String title, String className) {
        if (title == null || title.isEmpty() || title.equals("Program Manager")) {
            return false;
        }

        if (title.contains("网易云音乐") && !title.equals("网易云音乐")) {
            return true;
        }

        for (String targetClass : TARGET_CLASS_NAMES) {
            if (className.contains(targetClass)) {
                return true;
            }
        }

        return false;
    }
}
