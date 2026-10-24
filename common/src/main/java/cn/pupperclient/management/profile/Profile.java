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

package cn.pupperclient.management.profile;

import java.util.List;

import com.google.gson.JsonObject;
import cn.pupperclient.management.config.ConfigType;

import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;

public class Profile {

	private final String name, author;
	private final List<ObjectObjectImmutablePair<ConfigType, JsonObject>> configs;
	private final Object icon;
	private final String serverIp;

	public Profile(String name, String author, List<ObjectObjectImmutablePair<ConfigType, JsonObject>> configs,
			Object icon, String serverIp) {
		this.name = name;
		this.author = author;
		this.configs = configs;
		this.icon = icon;
		this.serverIp = serverIp;
	}

	public String getName() {
		return name;
	}

	public String getAuthor() {
		return author;
	}

	public List<ObjectObjectImmutablePair<ConfigType, JsonObject>> getConfigs() {
		return configs;
	}

	public Object getIcon() {
		return icon;
	}

	public String getServerIp() {
		return serverIp;
	}
}
