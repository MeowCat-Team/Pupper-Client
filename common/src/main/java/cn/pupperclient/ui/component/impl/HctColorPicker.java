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

package cn.pupperclient.ui.component.impl;

import java.awt.Color;

import org.lwjgl.glfw.GLFW;

import cn.pupperclient.PupperClient;
import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.component.handler.impl.HctColorPickerHandler;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.ui.theme.MaterialControls;
import cn.pupperclient.utils.mouse.MouseUtils;

public class HctColorPicker extends Component {

	private SimpleAnimation slideAnimation = new SimpleAnimation();

	private Hct hct;
	private float minValue, maxValue, value;
	private boolean dragging;

	public HctColorPicker(float x, float y, Hct hct) {
		super(x, y);
		this.hct = hct;
		minValue = 0;
		maxValue = 360;
		value = (float) (hct.getHue() - minValue) / (maxValue - minValue);
		width = MaterialControls.COMPACT_WIDTH;
		height = MaterialControls.COMPACT_HEIGHT;
	}

	@Override
	public void draw(double mouseX, double mouseY) {

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();

		float inset = 12;
		float travelWidth = width - inset * 2;
		slideAnimation.onTick(value, 20);
		MaterialControls.hue(x, y, width, height, slideAnimation.getValue(), new Color(hct.toInt(), true),
				palette, MaterialTheme.opacity(), hoverState(mouseX, mouseY));

		if (dragging) {

			value = (float) Math.min(1, Math.max(0, (mouseX - x - inset) / travelWidth));
			hct = Hct.from((value * (maxValue - minValue) + minValue), hct.getChroma(), hct.getTone());

			onPicking(hct);
		}
	}

	@Override
	public void mousePressed(double mouseX, double mouseY, int button) {

		if (MouseUtils.isInside(mouseX, mouseY, x, y, width, height) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			dragging = true;
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;
	}

	private void onPicking(Hct hct) {
		if (handler instanceof HctColorPickerHandler) {
			((HctColorPickerHandler) handler).onPicking(hct);
		}
	}
}
