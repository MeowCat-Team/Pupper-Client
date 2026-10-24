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

package cn.pupperclient.management.mod;

import java.util.ArrayList;
import java.util.List;

public enum ModCategory {
	ALL("text.all"), HUD("text.hud"), RENDER("text.render"), PLAYER("text.player"), MISC("text.misc"), FUN("text.fun"), HACK("text.hack");

	private final String title;

	private ModCategory(String title) {
		this.title = title;
	}

	public String getTitle() {
		return title;
	}

	public static List<String> getTitles() {

		List<String> output = new ArrayList<>();

		for (ModCategory c : ModCategory.values()) {
			output.add(c.getTitle());
		}

		return output;
	}
}
