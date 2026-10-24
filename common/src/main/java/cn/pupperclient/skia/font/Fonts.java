package cn.pupperclient.skia.font;

import io.github.humbleui.skija.Font;

public class Fonts {

	private static final String REGULAR = "Inter-Regular-CJKsc.ttf";
	private static final String MEDIUM = "Inter-Medium-CJKsc.ttf";
	private static final String OPEN_SANS_MEDIUM = "OpenSans-Medium.ttf";
	private static final String ICON_FILL = "MaterialSymbolsRounded_Fill.ttf";
	private static final String ICON = "MaterialSymbolsRounded.ttf";

	public static void loadAll() {
		FontHelper.preloadFonts(REGULAR, MEDIUM, OPEN_SANS_MEDIUM, ICON_FILL, ICON);
	}

	public static Font getRegular(float size) {
		return FontHelper.load(REGULAR, size);
	}

	public static Font getMedium(float size) {
		return FontHelper.load(MEDIUM, size);
	}

	public static Font getOpenSansMedium(float size) {
		return FontHelper.load(OPEN_SANS_MEDIUM, size);
	}

	public static Font getIconFill(float size) {
		return FontHelper.load(ICON_FILL, size);
	}

	public static Font getIcon(float size) {
		return FontHelper.load(ICON, size);
	}
}
