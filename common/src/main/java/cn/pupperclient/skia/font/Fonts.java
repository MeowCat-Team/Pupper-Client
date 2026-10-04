package cn.pupperclient.skia.font;

import io.github.humbleui.skija.Font;

public class Fonts {

	private static final String REGULAR = "Inter-Regular-CJKsc.ttf";
    private static final String GOOGLE_SANS_REGULAR = "GoogleSans-Regular.ttf";
	private static final String MEDIUM = "Inter-Medium-CJKsc.ttf";
	private static final String ICON_FILL = "MaterialSymbolsRounded_Fill.ttf";
	private static final String ICON = "MaterialSymbolsRounded.ttf";

	public static void loadAll() {
		FontHelper.preloadFonts(REGULAR, MEDIUM, ICON_FILL, ICON);
	}

	public static Font getRegular(float size) {
		return FontHelper.load(REGULAR, size);
	}

	public static Font getMedium(float size) {
		return FontHelper.load(MEDIUM, size);
	}

    public static Font getGoogleSansRegular(float size) {
		return FontHelper.load(GOOGLE_SANS_REGULAR, size);
	}

	public static Font getIconFill(float size) {
		return FontHelper.load(ICON_FILL, size);
	}

	public static Font getIcon(float size) {
		return FontHelper.load(ICON, size);
	}
}
