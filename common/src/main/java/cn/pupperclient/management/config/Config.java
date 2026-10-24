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

package cn.pupperclient.management.config;

import java.io.File;

import com.google.gson.JsonObject;
import cn.pupperclient.utils.file.FileLocation;

public abstract class Config {

	protected JsonObject jsonObject;
	private final ConfigType type;
	private final File file;

	public Config(ConfigType type) {
		this.type = type;
		this.file = new File(FileLocation.CONFIG_DIR, type.getId() + ".json");
		this.jsonObject = new JsonObject();
	}

	public abstract void onLoad();

	public abstract void onSave();

	public JsonObject getJsonObject() {
		return jsonObject;
	}

	public void setJsonObject(JsonObject jsonObject) {
		this.jsonObject = jsonObject;
	}

	public File getFile() {
		return file;
	}

	public ConfigType getType() {
		return type;
	}
}
