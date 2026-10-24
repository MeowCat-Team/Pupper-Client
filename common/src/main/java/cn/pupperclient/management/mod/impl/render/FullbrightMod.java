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

package cn.pupperclient.management.mod.impl.render;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.NumberSetting;
import cn.pupperclient.skia.font.Icon;

public class FullbrightMod extends Mod {

	private static FullbrightMod instance;
	private NumberSetting gammaSetting = new NumberSetting("setting.gamma", "setting.gamma.description", Icon.LIGHTBULB,
			this, 15, 1, 15, 1);

	public FullbrightMod() {
		super("mod.fullbright.name", "mod.fullbright.description", Icon.LIGHTBULB, ModCategory.RENDER);
		instance = this;
	}

	public static FullbrightMod getInstance() {
		return instance;
	}
	
	public float getGamma() {
		return gammaSetting.getValue();
	}
}
