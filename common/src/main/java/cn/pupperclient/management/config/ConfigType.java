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

public enum ConfigType {
	MOD("mod"),
    KEY("key");

	private final String id;

	private ConfigType(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public static ConfigType get(String id) {

		for (ConfigType t : ConfigType.values()) {
			if (t.getId().equals(id)) {
				return t;
			}
		}

		return null;
	}
}
