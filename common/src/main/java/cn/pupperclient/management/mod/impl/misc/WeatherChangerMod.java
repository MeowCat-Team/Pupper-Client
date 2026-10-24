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

import java.util.Arrays;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.ComboSetting;
import cn.pupperclient.skia.font.Icon;

public class WeatherChangerMod extends Mod {

	private static WeatherChangerMod instance;

	private ComboSetting weatherSetting = new ComboSetting("setting.weather", "setting.weather.description", Icon.CLOUD,
			this, Arrays.asList("setting.clear", "setting.rain", "setting.storm", "setting.snow"), "setting.clear");
	
	public WeatherChangerMod() {
		super("mod.weatherchanger.name", "mod.weatherchanger.description", Icon.SUNNY, ModCategory.MISC);

		instance = this;
	}

	public static WeatherChangerMod getInstance() {
		return instance;
	}
	
	public boolean isRaining() {
		return !weatherSetting.getOption().equals("setting.clear");
	}

	public boolean isSnowing() {
		return weatherSetting.getOption().equals("setting.snow");
	}

	public boolean isThundering() {
		return weatherSetting.getOption().equals("setting.storm");
	}
}
