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

import java.util.List;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.settings.Setting;

public class ComboSetting extends Setting {

	private List<String> options;
	private String defaultOption, option;

	public ComboSetting(String name, String description, String icon, Mod parent, List<String> options, String option) {
		super(name, description, icon, parent);
		this.options = options;
		this.option = option;
		this.defaultOption = option;

		PupperClient.getInstance().getModManager().addSetting(this);
	}

	@Override
	public void reset() {
		this.option = defaultOption;
	}

	public String getOption() {
		return option;
	}

	public void setOption(String option) {
		this.option = option;
	}

	public List<String> getOptions() {
		return options;
	}

	public String getDefaultOption() {
		return defaultOption;
	}

	public void setDefaultOption(String defaultOption) {
		this.defaultOption = defaultOption;
	}

	public boolean has(String s) {

		for (String option : options) {
			if (option.equals(s)) {
				return true;
			}
		}

		return false;
	}
}
