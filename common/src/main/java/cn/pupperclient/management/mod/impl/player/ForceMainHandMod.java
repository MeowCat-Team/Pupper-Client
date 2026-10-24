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

import java.util.Arrays;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.ComboSetting;
import cn.pupperclient.skia.font.Icon;

public class ForceMainHandMod extends Mod {

	private static ForceMainHandMod instance;
	private ComboSetting typeSetting = new ComboSetting("setting.type", "setting.type.description", Icon.FRONT_HAND,
			this, Arrays.asList("setting.left", "setting.right"), "setting.right");

	public ForceMainHandMod() {
		super("mod.forcemainhand.name", "mod.forcemainhand.description", Icon.FRONT_HAND, ModCategory.PLAYER);
		instance = this;
	}

	public static ForceMainHandMod getInstance() {
		return instance;
	}
	
	public boolean isRightHand() {
		return typeSetting.getOption().contains("right");
	}
}
