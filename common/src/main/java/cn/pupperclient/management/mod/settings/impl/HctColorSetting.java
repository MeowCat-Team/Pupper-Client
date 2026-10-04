package cn.pupperclient.management.mod.settings.impl;

import cn.pupperclient.PupperClient;
import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.settings.Setting;

public class HctColorSetting extends Setting {

	private Hct hct;
	private final Hct defaultHct;

	public HctColorSetting(String name, String description, String icon, Mod parent, Hct hct) {
		super(name, description, icon, parent);
		this.hct = hct;
		this.defaultHct = hct;

		PupperClient.getInstance().getModManager().addSetting(this);
	}

	@Override
	public void reset() {
		this.hct = defaultHct;
	}

	public Hct getHct() {
		return hct;
	}

	public void setHct(Hct hct) {
		this.hct = hct;
	}

	public Hct getDefaultHct() {
		return defaultHct;
	}
}
