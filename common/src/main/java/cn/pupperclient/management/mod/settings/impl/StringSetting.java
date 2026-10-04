package cn.pupperclient.management.mod.settings.impl;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.settings.Setting;

public class StringSetting extends Setting {

	private String defaultValue, value;

	public StringSetting(String name, String description, String icon, Mod parent, String string) {
		super(name, description, icon, parent);

		this.defaultValue = string;
		this.value = string;

		PupperClient.getInstance().getModManager().addSetting(this);
	}

	@Override
	public void reset() {
		this.value = defaultValue;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String string) {
		this.value = string;
	}

	public String getDefaultValue() {
		return defaultValue;
	}
}
