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

import io.github.humbleui.skija.Font;

public class Fonts {

	private static final String REGULAR = "Inter-Regular-CJKsc.ttf";
	private static final String MEDIUM = "Inter-Medium-CJKsc.ttf";
	private static final String OPEN_SANS_MEDIUM = "OpenSans-Medium.ttf";
	private static final String ICON_FILL = "MaterialSymbolsRounded_Fill.ttf";
	private static final String ICON = "MaterialSymbolsRounded.ttf";

	public static void loadAll() {
		FontHelper.preloadFonts(REGULAR, MEDIUM, OPEN_SANS_MEDIUM, ICON_FILL, ICON);
	}

	public static Font getRegular(float size) {
		return FontHelper.load(REGULAR, size);
	}

	public static Font getMedium(float size) {
		return FontHelper.load(MEDIUM, size);
	}

	public static Font getOpenSansMedium(float size) {
		return FontHelper.load(OPEN_SANS_MEDIUM, size);
	}

	public static Font getIconFill(float size) {
		return FontHelper.load(ICON_FILL, size);
	}

	public static Font getIcon(float size) {
		return FontHelper.load(ICON, size);
	}
}
