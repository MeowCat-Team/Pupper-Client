package cn.pupperclient.utils.render;

import java.lang.ref.WeakReference;

/** Extraction-thread budget. Density comes from the preceding visible frame, not a world scan. */
public final class ItemRenderBudget {
    private static final ThreadLocal<Frame> FRAME = ThreadLocal.withInitial(Frame::new);

    private ItemRenderBudget() { }

    public static void beginFrame(Object world, boolean enabled, int threshold) {
        Frame frame = FRAME.get();
        if (frame.world.get() != world || !enabled) frame.previousItems = 0;
        if (frame.world.get() != world) frame.world = new WeakReference<>(world);
        frame.active = enabled && world != null;
        frame.threshold = Math.max(1, threshold);
    }

    public static void endFrame(int visibleItems) {
        Frame frame = FRAME.get();
        frame.previousItems = frame.active ? Math.max(0, visibleItems) : 0;
        frame.active = false;
    }

    public static boolean isActive() {
        return FRAME.get().active;
    }

    public static boolean skipDistantShadow(double distanceSquared) {
        Frame frame = FRAME.get();
        if (!frame.active || frame.previousItems < frame.threshold) return false;
        // Keep a contact shadow close to the player. Skip terrain/light queries as well as
        // shadow draw submissions farther away; the item model itself is never culled.
        double radiusSquared = frame.previousItems >= (long) frame.threshold * 2 ? 16 : 64;
        return Double.isFinite(distanceSquared) && distanceSquared > radiusSquared;
    }

    private static final class Frame {
        WeakReference<Object> world = new WeakReference<>(null);
        int previousItems;
        int threshold;
        boolean active;
    }
}
