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

package cn.pupperclient.animation.cubicbezier.impl;

import cn.pupperclient.animation.cubicbezier.CubicBezier;

public class EaseStandardAccelerate extends CubicBezier {

	public EaseStandardAccelerate(float duration, float start, float end) {
		super(0.3F, 0F, 1F, 1F, duration, start, end);
	}
}
