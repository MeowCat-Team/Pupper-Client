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
