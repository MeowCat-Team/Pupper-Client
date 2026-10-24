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

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.settings.Setting;
import cn.pupperclient.utils.math.MathUtils;

public class NumberSetting extends Setting {

	private float defaultValue, value, minValue, maxValue;
	private float step;

	public NumberSetting(String name, String description, String icon, Mod parent, float defaultValue, float minValue,
			float maxValue, float step) {
		super(name, description, icon, parent);

		this.value = defaultValue;
		this.defaultValue = defaultValue;
		this.minValue = minValue;
		this.maxValue = maxValue;
		this.step = step;

		PupperClient.getInstance().getModManager().addSetting(this);
	}

	@Override
	public void reset() {
		setValue(defaultValue);
	}

	public float getValue() {
		return value;
	}

    public double getDoubleValue() {
        return value;
    }

	public float getMinValue() {
		return minValue;
	}

	public float getMaxValue() {
		return maxValue;
	}

	public void setValue(float value) {
		value = MathUtils.clamp(value, this.minValue, this.maxValue);
		this.value = value;
	}

	public float getStep() {
		return step;
	}

	public void setStep(float step) {
		this.step = step;
	}

	public float getDefaultValue() {
		return defaultValue;
	}
}
