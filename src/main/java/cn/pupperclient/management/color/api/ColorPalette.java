package cn.pupperclient.management.color.api;

import java.awt.Color;

import cn.pupperclient.libraries.material3.Material3;
import cn.pupperclient.libraries.material3.dynamiccolor.DynamicScheme;
import cn.pupperclient.libraries.material3.dynamiccolor.MaterialDynamicColors;
import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.libraries.material3.utils.ColorUtils;

public class ColorPalette {

	private final DynamicScheme scheme;
	private final MaterialDynamicColors colors;
	private final boolean dark;
	private final Hct hct;

	public ColorPalette(Hct hct, boolean dark, float contrast) {
		this.hct = Hct.fromInt(hct.toInt());
		this.dark = dark;
		scheme = Material3.getDynamicScheme(this.hct, dark, contrast);
		colors = new MaterialDynamicColors();
	}

	public ColorPalette(Hct hct, boolean dark) {
		this(hct, dark, 0);
	}

	public Color getPrimary() {
		return argbToColor(colors.primary().getArgb(scheme));
	}

	public Color getSecondary() {
		return argbToColor(colors.secondary().getArgb(scheme));
	}

	public Color getTertiary() {
		return argbToColor(colors.tertiary().getArgb(scheme));
	}

	public Color getPrimaryContainer() {
		return argbToColor(colors.primaryContainer().getArgb(scheme));
	}

	public Color getSecondaryContainer() {
		return argbToColor(colors.secondaryContainer().getArgb(scheme));
	}

	public Color getTertiaryContainer() {
		return argbToColor(colors.tertiaryContainer().getArgb(scheme));
	}

	public Color getOnPrimary() {
		return argbToColor(colors.onPrimary().getArgb(scheme));
	}

	public Color getOnSecondary() {
		return argbToColor(colors.onSecondary().getArgb(scheme));
	}

	public Color getOnTertiary() {
		return argbToColor(colors.onTertiary().getArgb(scheme));
	}

	public Color getOnPrimaryContainer() {
		return argbToColor(colors.onPrimaryContainer().getArgb(scheme));
	}

	public Color getOnSecondaryContainer() {
		return argbToColor(colors.onSecondaryContainer().getArgb(scheme));
	}

	public Color getOnTertiaryContainer() {
		return argbToColor(colors.onTertiaryContainer().getArgb(scheme));
	}

    public Color getWarning() {
		return argbToColor(colors.warning().getArgb(scheme));
	}

	public Color getError() {
		return argbToColor(colors.error().getArgb(scheme));
	}

	public Color getErrorContainer() {
		return argbToColor(colors.errorContainer().getArgb(scheme));
	}

	public Color getOnError() {
		return argbToColor(colors.onError().getArgb(scheme));
	}

	public Color getOnErrorContainer() {
		return argbToColor(colors.onErrorContainer().getArgb(scheme));
	}

	public Color getBackground() {
		return argbToColor(colors.background().getArgb(scheme));
	}

	public Color getOnBackground() {
		return argbToColor(colors.onBackground().getArgb(scheme));
	}

	public Color getSurface() {
		return argbToColor(colors.surface().getArgb(scheme));
	}

	public Color getOnSurface() {
		return argbToColor(colors.onSurface().getArgb(scheme));
	}

	public Color getSurfaceContainer() {
		return argbToColor(colors.surfaceContainer().getArgb(scheme));
	}

	public Color getSurfaceVariant() {
		return argbToColor(colors.surfaceVariant().getArgb(scheme));
	}

	public Color getOnSurfaceVariant() {
		return argbToColor(colors.onSurfaceVariant().getArgb(scheme));
	}

	public Color getSurfaceContainerHigh() {
		return argbToColor(colors.surfaceContainerHigh().getArgb(scheme));
	}

	public Color getSurfaceContainerHighest() {
		return argbToColor(colors.surfaceContainerHighest().getArgb(scheme));
	}

	public Color getSurfaceContainerLow() {
		return argbToColor(colors.surfaceContainerLow().getArgb(scheme));
	}

	public Color getSurfaceContainerLowest() {
		return argbToColor(colors.surfaceContainerLowest().getArgb(scheme));
	}

	public Color getSurfaceTint() {
		return argbToColor(colors.surfaceTint().getArgb(scheme));
	}

	public Color getOutline() {
		return argbToColor(colors.outline().getArgb(scheme));
	}

	public Color getOutlineVariant() {
		return argbToColor(colors.outlineVariant().getArgb(scheme));
	}

	private Color argbToColor(int argb) {

		int red = ColorUtils.redFromArgb(argb);
		int green = ColorUtils.greenFromArgb(argb);
		int blue = ColorUtils.blueFromArgb(argb);

		return new Color(red, green, blue);
	}

	public boolean isDarkMode() {
		return dark;
	}

	public Hct getHct() {
		return hct;
	}
}
