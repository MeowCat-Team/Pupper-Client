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

public abstract class Animation {
	protected final float duration;
	protected final float start;
	protected final float change;
	private long startedAtNanos;


	public Animation(float duration, float start, float end) {
		this.duration = duration;
		this.start = start;
		this.change = end - start;
		reset();
	}

	public float getValue() {
		float progress = getProgress();
		if (progress >= 1) {
			return start + change;
		}
		return animate(progress) * change + start;
	}

	public boolean isFinished() {
		return getProgress() >= 1;
	}

	public float getEnd() {
		return start + change;
	}

	protected abstract float animate(float x);

	public void reset() {
		startedAtNanos = System.nanoTime();
	}

	private float getProgress() {
		if (duration <= 0) {
			return 1;
		}
		double elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000.0;
		return (float) Math.min(1, elapsedMillis / duration);
	}
}
