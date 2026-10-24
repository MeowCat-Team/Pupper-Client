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

package cn.pupperclient.animation;

public class SimpleAnimation {

	private float currentValue;
	private boolean firstTick;
    private float targetValue;
    private long lastUpdateNanos;

	public SimpleAnimation() {
		this.firstTick = true;
        this.currentValue = 0;
        this.targetValue = 0;
		resetClock();
	}

    public void setValue(float value) {
        this.currentValue = value;
        this.targetValue = value;
		resetClock();
    }
    public void setTarget(float target) {
        this.targetValue = target;
		resetClock();
    }

    public void update(float speed) {
		approach(targetValue, speed);
    }

	public void onTick(float value, float speed) {
		if (firstTick) {
			currentValue = value;
			firstTick = false;
			resetClock();
		} else {
			approach(value, speed);
		}
	}

	private void approach(float value, float speed) {
		long now = System.nanoTime();
		float deltaSeconds = (now - lastUpdateNanos) / 1_000_000_000F;
		lastUpdateNanos = now;
		if (deltaSeconds <= 0) {
			return;
		}

		float factor = Math.min(1, Math.max(0, speed) * deltaSeconds);
		currentValue += (value - currentValue) * factor;
		if (Math.abs(value - currentValue) < 0.01F) {
			currentValue = value;
		}
	}

	private void resetClock() {
		lastUpdateNanos = System.nanoTime();
	}

	public float getValue() {
		return currentValue;
	}

	public void setFirstTick(boolean firstTick) {
		this.firstTick = firstTick;
		resetClock();
	}
}
