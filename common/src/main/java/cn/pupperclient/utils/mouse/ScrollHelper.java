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

package cn.pupperclient.utils.mouse;

import cn.pupperclient.animation.SimpleAnimation;

public class ScrollHelper {

	private SimpleAnimation animation = new SimpleAnimation();
	private float scroll, maxScroll, minScroll;

	public ScrollHelper() {
		maxScroll = Float.MAX_VALUE;
		minScroll = 0;
	}

	public void onScroll(double amount) {
		scroll += amount * 60;
	}

	public void onUpdate() {
		animation.onTick(scroll, 18);
		scroll = Math.max(Math.min(minScroll, scroll), -maxScroll);
	}

	public float getValue() {
		return animation.getValue();
	}

	public void setMaxScroll(float itemHeight, float itemSpace, int itemSize, int row, float height) {

		int totalItems = itemSize;
		int itemsPerRow = row;
		int rows = (int) Math.ceil((double) totalItems / itemsPerRow);
		float totalHeight = rows * (itemHeight + itemSpace);
		float viewportHeight = height;
		float maxScroll = Math.max(0, totalHeight - viewportHeight);

		this.maxScroll = maxScroll;
	}

	public void setMaxScroll(float totalHeight, float height) {

		float maxScroll = Math.max(0, totalHeight - height);

		this.maxScroll = maxScroll;
	}

	public void reset() {
		this.scroll = 0;
	}
}
