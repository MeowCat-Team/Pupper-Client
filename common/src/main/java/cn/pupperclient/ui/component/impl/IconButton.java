package cn.pupperclient.ui.component.impl;

import java.awt.Color;

import cn.pupperclient.PupperClient;
import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.component.api.PressAnimation;
import cn.pupperclient.ui.component.handler.impl.ButtonHandler;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.ui.theme.MaterialControls;
import cn.pupperclient.utils.mouse.MouseUtils;

public class IconButton extends Component {

	private SimpleAnimation focusAnimation = new SimpleAnimation();
	private PressAnimation pressAnimation;

	private String icon;
	private Size size;
	private Style style;

	public IconButton(String icon, float x, float y, Size size, Style style) {
		super(x, y);

		this.icon = icon;
		this.size = size;
		this.style = style;
		float[] s = getPanelSize();

		width = s[0];
		height = s[1];
		pressAnimation = new PressAnimation();
	}

	@Override
	public void draw(double mouseX, double mouseY) {

		boolean focus = MouseUtils.isInside(mouseX, mouseY, x, y, width, height);

		Color[] c = getColor();
		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();

		focusAnimation.onTick(focus ? 1F : 0, 10);

		MaterialControls.iconButton(x, y, width, height, getRadius(), getFontSize(), icon, c[0], c[1], palette,
				MaterialTheme.opacity(), focusAnimation.getValue());
		Skia.save();
		try {
			Skia.clip(x, y, width, height, getRadius());
			pressAnimation.draw(x, y, width, height, c[1], 0.12F);
		} finally {
			Skia.restore();
		}
	}

	@Override
	public void mousePressed(double mouseX, double mouseY, int button) {
		if (MouseUtils.isInside(mouseX, mouseY, x, y, width, height) && button == 0) {
			pressAnimation.onPressed(mouseX, mouseY, x, y);
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		if (MouseUtils.isInside(mouseX, mouseY, x, y, width, height) && button == 0) {
			if (handler instanceof ButtonHandler) {
				((ButtonHandler) handler).onAction();
			}
		}
		pressAnimation.onReleased(mouseX, mouseY, x, y);
	}

	private float[] getPanelSize() {
		switch (size) {
		case LARGE:
			return new float[] { 64, 64 };
		case NORMAL:
			return new float[] { 56, 56 };
		case SMALL:
			return new float[] { 40, 40 };
		default:
			return new float[] { 0, 0 };
		}
	}

	private float getFontSize() {
		switch (size) {
		case LARGE:
			return 30;
		case NORMAL:
			return 24;
		case SMALL:
			return 24;
		default:
			return 0F;
		}
	}

	private float getRadius() {
		switch (size) {
		case LARGE:
			return 20;
		case NORMAL:
			return 16;
		case SMALL:
			return MaterialTheme.CONTROL_RADIUS;
		default:
			return 0F;
		}
	}

	private Color[] getColor() {

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();

		switch (style) {
		case PRIMARY:
			return new Color[] { palette.getPrimaryContainer(), palette.getOnPrimaryContainer() };
		case SECONDARY:
			return new Color[] { palette.getSecondaryContainer(), palette.getOnSecondaryContainer() };
		case SURFACE:
			return new Color[] { palette.getSurfaceContainer(), palette.getPrimary() };
		case TERTIARY:
			return new Color[] { palette.getTertiaryContainer(), palette.getOnTertiaryContainer() };
		default:
			return new Color[] { palette.getSurfaceContainer(), palette.getOnSurface() };
		}
	}

	public enum Size {
		SMALL, NORMAL, LARGE;
	}

	public enum Style {
		SURFACE, PRIMARY, SECONDARY, TERTIARY;
	}
}
