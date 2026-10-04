package cn.pupperclient.skia.font;

public enum FontType {
	TTF("ttf"), OTF("otf");

	private final String string;

	private FontType(String string) {
		this.string = string;
	}

	public static FontType fromString(String extension) {
		String normalizedExtension = extension.toLowerCase();
		return switch (normalizedExtension) {
		case "ttf" -> TTF;
		case "otf" -> OTF;
		default -> throw new IllegalArgumentException("Unsupported font type: " + extension);
		};
	}

	@Override
	public String toString() {
		return string;
	}
}
