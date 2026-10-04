package cn.pupperclient.management.profile;

public enum ProfileIcon {
	COMMAND("command"), CRAFTING_TABLE("crafting_table"), FURNACE("furnace"), GRASS("grass"), HAY("hay"),
	PUMPKIN("pumpkin"), TNT("tnt"), CUSTOM("custom");

	private final String id;
	private final String iconPath;

	private ProfileIcon(String id) {
		this.id = id;
		this.iconPath = "icons/" + id + ".png";
	}

	public String getId() {
		return id;
	}

	public String getIconPath() {
		return iconPath;
	}

	public static ProfileIcon getIconById(String id) {

		for (ProfileIcon pi : ProfileIcon.values()) {
			if (pi.getId().equals(id)) {
				return pi;
			}
		}

		return ProfileIcon.GRASS;
	}
}
