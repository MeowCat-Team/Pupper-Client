package cn.pupperclient.management.mod;

import java.util.ArrayList;
import java.util.List;

public enum ModCategory {
	ALL("text.all"), HUD("text.hud"), RENDER("text.render"), PLAYER("text.player"), MISC("text.misc"), FUN("text.fun"), HACK("text.hack");

	private final String title;

	private ModCategory(String title) {
		this.title = title;
	}

	public String getTitle() {
		return title;
	}

	public static List<String> getTitles() {

		List<String> output = new ArrayList<>();

		for (ModCategory c : ModCategory.values()) {
			output.add(c.getTitle());
		}

		return output;
	}
}
