package cn.pupperclient.utils.language;

public enum Language {
	ENGLISH("en"),
	CHINESE("cn");

	private final String id;

	private Language(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}
}
