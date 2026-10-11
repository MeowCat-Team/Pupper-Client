package cn.pupperclient.utils.minecraft.item;

import java.util.Objects;

/** Selection changes reveal the card; consuming items or losing durability does not extend it. */
public final class HeldItemTracker {
    public static final int DISPLAY_TICKS = 80;
    private Object context, identity;
    private int slot = -1, remaining;

    public void update(Object context, int slot, Object identity) {
        if (context == null || identity == null) { reset(); return; }
        boolean changed = this.context != context || this.slot != slot || !Objects.equals(this.identity, identity);
        this.context = context; this.slot = slot; this.identity = identity;
        remaining = changed ? DISPLAY_TICKS : Math.max(0, remaining - 1);
    }
    public boolean visible() { return remaining > 0; }
    public void reset() { context = identity = null; slot = -1; remaining = 0; }
}
