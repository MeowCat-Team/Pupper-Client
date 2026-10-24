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

import java.awt.Color;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.settings.Setting;
import cn.pupperclient.utils.color.ColorUtils;

public class ColorSetting extends Setting {

	private Color defaultColor;
	private float hue;
	private float saturation;
	private float brightness;
	private float alpha;
	private boolean showAlpha;

	public ColorSetting(String name, String description, String icon, Mod parent, Color color, boolean showAlpha) {
		super(name, description, icon, parent);

		this.defaultColor = color;
		this.showAlpha = showAlpha;
		this.alpha = color.getAlpha() / 255F;
		this.setColor(color);

		PupperClient.getInstance().getModManager().addSetting(this);
	}

	@Override
	public void reset() {
		setColor(defaultColor);
	}

	public Color getColor() {
		return ColorUtils.applyAlpha(Color.getHSBColor(hue, saturation, brightness), showAlpha ? alpha : 1F);
	}

	public void setColor(Color color) {

		float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);

		hue = hsb[0];
		saturation = hsb[1];
		brightness = hsb[2];
		alpha = color.getAlpha() / 255F;
	}

	public boolean isShowAlpha() {
		return showAlpha;
	}

	public void setShowAlpha(boolean showAlpha) {
		this.showAlpha = showAlpha;
	}

	public void setAlpha(float alpha) {
		this.alpha = alpha;
	}
}
