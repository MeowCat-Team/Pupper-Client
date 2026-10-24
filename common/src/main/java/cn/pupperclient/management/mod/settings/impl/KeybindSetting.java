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

package cn.pupperclient.management.mod.settings.impl;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.settings.Setting;
import com.mojang.blaze3d.platform.InputConstants;

public class KeybindSetting extends Setting {
	private final InputConstants.Key defaultKey;
    private InputConstants.Key key;
	private boolean keyDown;
	private int pressTime;

	public KeybindSetting(String name, String description, String icon, Mod parent, InputConstants.Key key) {
		super(name, description, icon, parent);

		this.defaultKey = key;
		this.key = key;

		PupperClient.getInstance().getModManager().addSetting(this);
	}

	@Override
	public void reset() {
		this.key = this.defaultKey;
	}

	public InputConstants.Key getKey() {
		return key;
	}

	public void setKey(InputConstants.Key key) {
		this.key = key;
	}

	public InputConstants.Key getDefaultKey() {
		return defaultKey;
	}

	public boolean isKeyDown() {
		return keyDown;
	}

	public void setKeyDown(boolean keyDown) {
		this.keyDown = keyDown;
	}

	public boolean isPressed() {
		this.pressTime--;
		return pressTime >= 0;
	}

	public void setPressed() {
		this.pressTime = 1;
	}
}
