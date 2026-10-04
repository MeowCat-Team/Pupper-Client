package cn.pupperclient.management.profile;

import java.util.List;

import com.google.gson.JsonObject;
import cn.pupperclient.management.config.ConfigType;

import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;

public class Profile {

	private final String name, author;
	private final List<ObjectObjectImmutablePair<ConfigType, JsonObject>> configs;
	private final Object icon;
	private final String serverIp;

	public Profile(String name, String author, List<ObjectObjectImmutablePair<ConfigType, JsonObject>> configs,
			Object icon, String serverIp) {
		this.name = name;
		this.author = author;
		this.configs = configs;
		this.icon = icon;
		this.serverIp = serverIp;
	}

	public String getName() {
		return name;
	}

	public String getAuthor() {
		return author;
	}

	public List<ObjectObjectImmutablePair<ConfigType, JsonObject>> getConfigs() {
		return configs;
	}

	public Object getIcon() {
		return icon;
	}

	public String getServerIp() {
		return serverIp;
	}
}
