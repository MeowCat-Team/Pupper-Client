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

package cn.pupperclient.skia.font;

public enum FontType {
	TTF("ttf"), OTF("otf");

	private final String string;

	private FontType(String string) {
		this.string = string;
	}

	public static FontType fromString(String extension) {
		String normalizedExtension = extension.toLowerCase();
		return switch (normalizedExtension) {
		case "ttf" -> TTF;
		case "otf" -> OTF;
		default -> throw new IllegalArgumentException("Unsupported font type: " + extension);
		};
	}

	@Override
	public String toString() {
		return string;
	}
}
