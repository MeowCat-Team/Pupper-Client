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
