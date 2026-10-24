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

package cn.pupperclient.management.color;

import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.mod.impl.settings.ModMenuSettings;

import java.awt.*;
import java.util.ArrayList;

public class ColorManager {

    private ArrayList<AccentColor> colors = new ArrayList<AccentColor>();
	private ColorPalette palette;
    private AccentColor currentColor;

	public ColorManager() {
		updatePalette();
	}

	public void onTick() {

		ModMenuSettings m = ModMenuSettings.getInstance();
		Hct hct = m.getHctColorSetting().getHct();

        if (palette == null || palette.isDarkMode() != m.getDarkModeSetting().isEnabled()
                || palette.getHct().toInt() != hct.toInt()) {
            updatePalette();
        }
	}

	private void updatePalette() {

		ModMenuSettings m = ModMenuSettings.getInstance();

		palette = new ColorPalette(m.getHctColorSetting().getHct(), m.getDarkModeSetting().isEnabled());
	}

	public ColorPalette getPalette() {
		return palette;
	}

    public AccentColor getCurrentColor() {
        return currentColor;
    }

    public void setCurrentColor(AccentColor currentColor) {
        this.currentColor = currentColor;
    }

    private void add(String name, Color color1, Color color2) {
        colors.add(new AccentColor(name, color1, color2));
    }

    public ArrayList<AccentColor> getColors() {
        return colors;
    }
}
