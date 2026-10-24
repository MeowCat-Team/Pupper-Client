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

package cn.pupperclient.ui.component.api;

import java.awt.Color;

import cn.pupperclient.animation.Animation;
import cn.pupperclient.animation.Duration;
import cn.pupperclient.animation.cubicbezier.impl.EaseEmphasizedDecelerate;
import cn.pupperclient.animation.other.DummyAnimation;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.utils.color.ColorUtils;
import cn.pupperclient.utils.math.MathUtils;

public class PressAnimation {

	private Animation animation;
	private double[] pressedPos;

	public PressAnimation() {
		animation = new DummyAnimation();
		pressedPos = new double[] { 0, 0 };
	}

	public void setPressed() {
		animation = new DummyAnimation(1);
	}

	public void draw(float x, float y, float width, float height, Color color, float alpha) {

		float radius = calculateMaxRadius(width, height) * 1.1F;

		float resultAlpha = alpha;

		if (animation.getEnd() == 1) {
			resultAlpha = animation.getValue() * resultAlpha;
		} else if (animation.getEnd() == 2) {
			resultAlpha = (2 - animation.getValue()) * resultAlpha;
		} else {
			resultAlpha = 0;
		}

		if (animation.getEnd() == 2 && animation.isFinished()) {
			animation = new DummyAnimation();
		}

		Skia.drawCircle((float) (x + pressedPos[0]), (float) (y + pressedPos[1]),
				radius * MathUtils.clamp(animation.getValue(), 0, 1), ColorUtils.applyAlpha(color, resultAlpha));
	}

	public void onPressed(double mouseX, double mouseY, double x, double y) {
		animation = new EaseEmphasizedDecelerate(Duration.EXTRA_LONG_2, 0, 1);
		pressedPos = new double[] { mouseX - x, mouseY - y };
	}

	public void onReleased(double mouseX, double mouseY, double x, double y) {
		if (animation.getEnd() == 1) {
			animation = new EaseEmphasizedDecelerate(Duration.EXTRA_LONG_2, animation.getValue(), 2);
		}
		pressedPos = new double[] { mouseX - x, mouseY - y };
	}

	private float calculateMaxRadius(float width, float height) {
		return (float) Math.sqrt(Math.pow(width, 2) + Math.pow(height, 2));
	}
}
