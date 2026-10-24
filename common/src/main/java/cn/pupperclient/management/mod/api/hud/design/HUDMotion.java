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

package cn.pupperclient.management.mod.api.hud.design;

/** Time-based, interruptible motion shared by HUD components. */
public final class HUDMotion {
    private long lastFrame;
    public float deltaSeconds() {
        long now = System.nanoTime();
        float delta = lastFrame == 0 ? 1f / 60 : (now - lastFrame) / 1_000_000_000f;
        lastFrame = now;
        return Math.min(0.1f, Math.max(0, delta));
    }

    public static float approach(float value, float target, float seconds, boolean reduced) {
        if (reduced || Math.abs(target - value) < 0.01f) return target;
        return target + (value - target) * (float) Math.exp(-18 * seconds);
    }

    /** Critically damped spatial spring; keeps velocity when its target changes. */
    public static final class Spring {
        private float value;
        private float velocity;
        public Spring(float initial) { value = initial; }
        public float update(float target, float seconds, boolean reduced) {
            if (reduced) {
                value = target;
                velocity = 0;
                return value;
            }
            double decay = Math.exp(-20 * seconds);
            float offset = value - target;
            float impulse = velocity + 20 * offset;
            value = target + (float) ((offset + impulse * seconds) * decay);
            velocity = (float) ((velocity - 20 * impulse * seconds) * decay);
            if (Math.abs(value - target) < 0.01f && Math.abs(velocity) < 0.01f) {
                value = target;
                velocity = 0;
            }
            return value;
        }
    }
}