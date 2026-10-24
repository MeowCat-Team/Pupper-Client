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
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.component.api.PressAnimation;
import cn.pupperclient.ui.component.handler.impl.ButtonHandler;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.ui.theme.MaterialControls;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.mouse.MouseUtils;

import io.github.humbleui.types.Rect;

public class Button extends Component {

	private PressAnimation pressAnimation = new PressAnimation();

	private String text;
	private Style style;

	public Button(String text, float x, float y, Style style) {
		super(x, y);
		this.text = text;
		this.height = 40;
		this.style = style;
		Rect bounds = Skia.getTextBounds(I18n.get(text), Fonts.getRegular(16));
		this.width = bounds.getWidth() + (24 * 2);
	}

	@Override
	public void draw(double mouseX, double mouseY) {

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();
		float radius = height / 2;
		MaterialControls.ButtonStyle paintStyle = MaterialControls.ButtonStyle.valueOf(style.name());
		Color content = MaterialControls.buttonContent(palette, paintStyle);
		MaterialControls.button(x, y, width, height, I18n.get(text), palette, MaterialTheme.opacity(), paintStyle,
				hoverState(mouseX, mouseY));
		Skia.save();
		try {
			Skia.clip(x, y, width, height, radius);
			pressAnimation.draw(x, y, width, height, content, 0.12F);
		} finally {
			Skia.restore();
		}
	}

	@Override
	public void mousePressed(double mouseX, double mouseY, int button) {

		if (MouseUtils.isInside(mouseX, mouseY, x, y, width, height) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			pressAnimation.onPressed(mouseX, mouseY, x, y);
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		if (MouseUtils.isInside(mouseX, mouseY, x, y, width, height) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			if (handler instanceof ButtonHandler) {
				((ButtonHandler) handler).onAction();
			}
		}
		pressAnimation.onReleased(mouseX, mouseY, x, y);
	}

	public enum Style {
		FILLED, ELEVATED, TONAL
	}
}
