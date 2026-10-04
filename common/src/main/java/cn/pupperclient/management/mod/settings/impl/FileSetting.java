package cn.pupperclient.management.mod.settings.impl;

import java.io.File;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.settings.Setting;

public class FileSetting extends Setting {

	private final File defaultValue;
	private File file;
	private String[] extensions;
	
	public FileSetting(String name, String description, String icon, Mod parent, File file, String... extensions) {
		super(name, description, icon, parent);
		this.defaultValue = file;
		this.file = file;
		this.extensions = extensions;
		PupperClient.getInstance().getModManager().addSetting(this);
	}

	@Override
	public void reset() {
		this.file = this.defaultValue;
	}

	public File getDefaultValue() {
		return defaultValue;
	}

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public String[] getExtensions() {
		return extensions;
	}
}
