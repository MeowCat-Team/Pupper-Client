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

package cn.pupperclient.management.mod.impl.misc;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.NumberSetting;
import cn.pupperclient.skia.font.Icon;

public class TimeChangerMod extends Mod {

	private static TimeChangerMod instance;
	private NumberSetting timeSetting = new NumberSetting("setting.time", "setting.time.description", Icon.UPDATE, this,
			12, 0, 24, 1);

	public TimeChangerMod() {
		super("mod.timechanger.name", "mod.timechanger.description", Icon.UPDATE, ModCategory.MISC);

		instance = this;
	}

	public static TimeChangerMod getInstance() {
		return instance;
	}

	public long getTime() {
		return (long) (timeSetting.getValue() * 1000L) + 18000L;
	}
}
