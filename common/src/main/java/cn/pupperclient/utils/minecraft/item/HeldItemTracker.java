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
