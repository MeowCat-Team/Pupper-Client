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

public class RippleEffect {

    private final float x;
    private final float y;
    private final long startTime;
    private final float maxRadius;
    private final long lifetime;
    private final float maxAlpha;

    private float radius = 5.0f;
    private float innerAlpha = 1.0f;
    private float outerAlpha = 1.0f;

    public RippleEffect(float x, float y, float maxRadius, long lifetime) {
        this.x = x;
        this.y = y;
        this.maxRadius = maxRadius;
        this.lifetime = lifetime;
        this.maxAlpha = 1.0f;
        this.startTime = System.currentTimeMillis();
    }

    public void update() {
        long elapsed = System.currentTimeMillis() - startTime;
        float progress = Math.min(1.0f, (float) elapsed / lifetime);

        float easeOut = (float) (1 - Math.pow(1 - progress, 3));

        float innerFade = 1 - (float) Math.pow(progress, 2);
        float outerFade = 1 - progress;

        this.radius = easeOut * maxRadius;
        this.innerAlpha = innerFade * maxAlpha;
        this.outerAlpha = outerFade * maxAlpha;
    }

    public boolean isAlive() {
        return System.currentTimeMillis() - startTime < lifetime;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getRadius() {
        return radius;
    }

    public float getInnerAlpha() {
        return innerAlpha;
    }

    public float getOuterAlpha() {
        return outerAlpha;
    }

    public float getProgress() {
        return (float) (System.currentTimeMillis() - startTime) / lifetime;
    }
}
