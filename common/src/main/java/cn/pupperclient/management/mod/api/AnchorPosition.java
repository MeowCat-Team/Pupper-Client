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

package cn.pupperclient.management.mod.api;

public enum AnchorPosition {
	TOP_LEFT(0, 0f, 0f), TOP_CENTER(1, 0.5f, 0f), TOP_RIGHT(2, 1f, 0f), MIDDLE_LEFT(3, 0f, 0.5f),
	MIDDLE_CENTER(4, 0.5f, 0.5f), MIDDLE_RIGHT(5, 1f, 0.5f), BOTTOM_LEFT(6, 0f, 1f), BOTTOM_CENTER(7, 0.5f, 1f),
	BOTTOM_RIGHT(8, 1f, 1f);

	private final int id;
	private final float x;
	private final float y;

	AnchorPosition(int id, float x, float y) {
		this.id = id;
		this.x = x;
		this.y = y;
	}

	public int getId() {
		return id;
	}

	public float getX() {
		return x;
	}

	public float getY() {
		return y;
	}

	public static AnchorPosition get(int id) {

		for (AnchorPosition pos : AnchorPosition.values()) {
			if (pos.getId() == id) {
				return pos;
			}
		}

		return TOP_LEFT;
	}
}
