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

package cn.pupperclient.gui.api.page.impl;

import cn.pupperclient.animation.Animation;
import cn.pupperclient.gui.api.page.GuiTransition;

public class LeftTransition extends GuiTransition {

	public LeftTransition(boolean consecutive) {
		super(consecutive);
	}

	@Override
	public float[] onTransition(Animation animation) {

		float progress = animation.getValue();
		float x = 0;

		if (animation.getEnd() == 1) {
			x = -1 + progress;
		} else {
			x = -1 + progress;
		}

		return new float[] { x, 0 };
	}
}
