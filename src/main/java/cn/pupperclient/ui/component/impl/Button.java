package cn.pupperclient.ui.component.impl;

import java.awt.Color;

import org.lwjgl.glfw.GLFW;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.component.api.PressAnimation;
import cn.pupperclient.ui.component.handler.impl.ButtonHandler;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.mouse.MouseUtils;

import io.github.humbleui.types.Rect;

public class Button extends Component {

	private PressAnimation pressAnimation = new PressAnimation();

	private String text;
	private Style style;

	public Button(String text, float x, float y, Style style) {
		super(x, y);
		this.text = text;
		this.height = 40;
		this.style = style;
		Rect bounds = Skia.getTextBounds(I18n.get(text), Fonts.getRegular(16));
		this.width = bounds.getWidth() + (24 * 2);
	}

	@Override
	public void draw(double mouseX, double mouseY) {

		Color[] colors = getColor();
		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();
		float radius = height / 2;

		Skia.drawRoundedRect(x, y, width, height, radius,
				style == Style.FILLED ? colors[0] : MaterialTheme.surface(colors[0]));
		if (style != Style.FILLED) {
			MaterialTheme.outline(x, y, width, height, radius, palette);
		}
		drawHoverState(mouseX, mouseY, radius, colors[1]);
		Skia.save();
		Skia.clip(x, y, width, height, radius);
		pressAnimation.draw(x, y, width, height, colors[1], 0.12F);
		Skia.restore();
		Skia.drawFullCenteredText(I18n.get(text), x + (width / 2), y + (height / 2), colors[1], Fonts.getRegular(16));
	}

	@Override
	public void mousePressed(double mouseX, double mouseY, int button) {

		if (MouseUtils.isInside(mouseX, mouseY, x, y, width, height) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			pressAnimation.onPressed(mouseX, mouseY, x, y);
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		if (MouseUtils.isInside(mouseX, mouseY, x, y, width, height) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			if (handler instanceof ButtonHandler) {
				((ButtonHandler) handler).onAction();
			}
		}
		pressAnimation.onReleased(mouseX, mouseY, x, y);
	}

	private Color[] getColor() {

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();

		switch (style) {
		case ELEVATED:
			return new Color[] { palette.getSurfaceContainerLow(), palette.getPrimary() };
		case FILLED:
			return new Color[] { palette.getPrimary(), palette.getOnPrimary() };
		case TONAL:
			return new Color[] { palette.getSecondaryContainer(), palette.getOnSecondaryContainer() };
		default:
			return new Color[] { palette.getSurfaceContainer(), palette.getOnSurface() };
		}
	}

	public enum Style {
		FILLED, ELEVATED, TONAL
	}
}
