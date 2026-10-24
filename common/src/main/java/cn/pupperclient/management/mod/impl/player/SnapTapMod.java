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

package cn.pupperclient.management.mod.impl.player;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.skia.font.Icon;

public class SnapTapMod extends Mod {

	private static SnapTapMod instance;
	private long leftPressTime, rightPressTime, forwardPressTime, backPressTime;

	public SnapTapMod() {
		super("mod.snaptap.name", "mod.snaptap.description", Icon.KEYBOARD_KEYS, ModCategory.PLAYER);
		instance = this;
	}

	public static SnapTapMod getInstance() {
		return instance;
	}

	public long getLeftPressTime() {
		return leftPressTime;
	}

	public void setLeftPressTime(long leftPressTime) {
		this.leftPressTime = leftPressTime;
	}

	public long getRightPressTime() {
		return rightPressTime;
	}

	public void setRightPressTime(long rightPressTime) {
		this.rightPressTime = rightPressTime;
	}

	public long getForwardPressTime() {
		return forwardPressTime;
	}

	public void setForwardPressTime(long forwardPressTime) {
		this.forwardPressTime = forwardPressTime;
	}

	public long getBackPressTime() {
		return backPressTime;
	}

	public void setBackPressTime(long backPressTime) {
		this.backPressTime = backPressTime;
	}
}
