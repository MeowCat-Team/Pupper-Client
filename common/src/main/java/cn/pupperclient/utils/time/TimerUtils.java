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

package cn.pupperclient.utils.time;

public class TimerUtils {

	public long lastMs;

	public TimerUtils() {
		this.lastMs = 0L;
	}

	public void reset() {
		this.lastMs = System.currentTimeMillis();
	}

	public boolean delay(long nextDelay) {
		return System.currentTimeMillis() - lastMs >= nextDelay;
	}

	public boolean delay(float nextDelay, boolean reset) {
		if (System.currentTimeMillis() - lastMs >= nextDelay) {
			if (reset) {
				this.reset();
			}
			return true;
		}
		return false;
	}

	public boolean isDelayComplete(double valueState) {
		return System.currentTimeMillis() - lastMs >= valueState;
	}

	public long getElapsedTime() {
		return System.currentTimeMillis() - this.lastMs;
	}

	public long getLastMs() {
		return lastMs;
	}

	public void setLastMs(long lastMs) {
		this.lastMs = lastMs;
	}
}
