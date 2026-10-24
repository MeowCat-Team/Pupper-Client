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

public enum ProfileIcon {
	COMMAND("command"), CRAFTING_TABLE("crafting_table"), FURNACE("furnace"), GRASS("grass"), HAY("hay"),
	PUMPKIN("pumpkin"), TNT("tnt"), CUSTOM("custom");

	private final String id;
	private final String iconPath;

	private ProfileIcon(String id) {
		this.id = id;
		this.iconPath = "icons/" + id + ".png";
	}

	public String getId() {
		return id;
	}

	public String getIconPath() {
		return iconPath;
	}

	public static ProfileIcon getIconById(String id) {

		for (ProfileIcon pi : ProfileIcon.values()) {
			if (pi.getId().equals(id)) {
				return pi;
			}
		}

		return ProfileIcon.GRASS;
	}
}
