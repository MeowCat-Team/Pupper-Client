package cn.pupperclient.management.config;

import java.io.File;

import com.google.gson.JsonObject;
import cn.pupperclient.utils.file.FileLocation;

public abstract class Config {

	protected JsonObject jsonObject;
	private final ConfigType type;
	private final File file;

	public Config(ConfigType type) {
		this.type = type;
		this.file = new File(FileLocation.CONFIG_DIR, type.getId() + ".json");
		this.jsonObject = new JsonObject();
	}

	public abstract void onLoad();

	public abstract void onSave();

	public JsonObject getJsonObject() {
		return jsonObject;
	}

	public void setJsonObject(JsonObject jsonObject) {
		this.jsonObject = jsonObject;
	}

	public File getFile() {
		return file;
	}

	public ConfigType getType() {
		return type;
	}
}
