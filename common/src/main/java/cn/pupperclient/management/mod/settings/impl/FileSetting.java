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

import java.io.File;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.settings.Setting;

public class FileSetting extends Setting {

	private final File defaultValue;
	private File file;
	private String[] extensions;
	
	public FileSetting(String name, String description, String icon, Mod parent, File file, String... extensions) {
		super(name, description, icon, parent);
		this.defaultValue = file;
		this.file = file;
		this.extensions = extensions;
		PupperClient.getInstance().getModManager().addSetting(this);
	}

	@Override
	public void reset() {
		this.file = this.defaultValue;
	}

	public File getDefaultValue() {
		return defaultValue;
	}

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public String[] getExtensions() {
		return extensions;
	}
}
