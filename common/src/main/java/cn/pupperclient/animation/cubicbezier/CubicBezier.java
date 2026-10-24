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

package cn.pupperclient.animation.cubicbezier;

import cn.pupperclient.animation.Animation;

public class CubicBezier extends Animation {

	private static final float CubicErrorBound = 0.001f;
	protected final float x1;
	protected final float y1;
	protected final float x2;
	protected final float y2;

	public CubicBezier(float x1, float y1, float x2, float y2, float duration, float start, float end) {
		super(duration, start, end);
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
	}

	@Override
	protected float animate(float x) {
		float start = 0.0f;
		float end = 1.0f;
		while (true) {
			float midpoint = (start + end) / 2;
			float estimate = evaluateCubic(x1, x2, midpoint);
			if (Math.abs(x - estimate) < CubicErrorBound)
				return evaluateCubic(y1, y2, midpoint);
			if (estimate < x)
				start = midpoint;
			else
				end = midpoint;
		}
	}

    private float evaluateCubic(float a, float b, float m) {
		return 3 * a * (1 - m) * (1 - m) * m + 3 * b * (1 - m) * m * m + m * m * m;
	}
}
