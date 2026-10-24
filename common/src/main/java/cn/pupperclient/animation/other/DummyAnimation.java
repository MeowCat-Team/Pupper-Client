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

package cn.pupperclient.animation.other;

import cn.pupperclient.animation.Animation;

public class DummyAnimation extends Animation {

	private float value;

	public DummyAnimation(float duration, float value) {
		super(duration, value, value);
		this.value = value;
	}

	public DummyAnimation(float value) {
		this(1, value);
	}

	public DummyAnimation() {
		this(1, 0);
	}

	@Override
	public float getValue() {
		return value;
	}

	@Override
	protected float animate(float x) {
		return x;
	}

}
