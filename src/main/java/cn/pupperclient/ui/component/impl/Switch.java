package cn.pupperclient.ui.component.impl;

import java.awt.Color;

import org.lwjgl.glfw.GLFW;

import cn.pupperclient.PupperClient;
import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.component.handler.impl.SwitchHandler;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.utils.color.ColorUtils;
import cn.pupperclient.utils.mouse.MouseUtils;

public class Switch extends Component {

	private SimpleAnimation enableAnimation = new SimpleAnimation();
	private SimpleAnimation pressAnimation = new SimpleAnimation();
	private SimpleAnimation focusAnimation = new SimpleAnimation();
	private boolean pressed;
	private boolean enabled;

	public Switch(float x, float y, boolean enabled) {
		super(x, y);
		this.width = 52;
		this.height = 32;
		this.enabled = enabled;
		this.pressed = false;
	}

	@Override
	public void draw(double mouseX, double mouseY) {

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();
		boolean focus = MouseUtils.isInside(mouseX, mouseY, x, y, width, height);

		Skia.drawRoundedRect(x, y, width, height, 16, MaterialTheme.surface(palette.getSurfaceContainerHighest()));
		Skia.drawOutline(x, y, width, height, 16, 2, MaterialTheme.alpha(palette.getOutline(), 0.75F));

		enableAnimation.onTick(enabled ? 1 : 0, 12);
		pressAnimation.onTick(pressed ? 1 : 0, 12);
		focusAnimation.onTick(focus ? 1 : 0, 10);

		Skia.drawRoundedRect(x, y, width, height, 16,
				ColorUtils.applyAlpha(palette.getPrimary(), enableAnimation.getValue()));

		Color fc = enabled ? palette.getPrimaryContainer() : palette.getOnSurfaceVariant();
		Color ec = enabled ? palette.getOnPrimary() : palette.getOutline();

		Color pc = enabled ? palette.getPrimary() : palette.getOnSurface();

		Skia.drawCircle(x + 16 + (20 * enableAnimation.getValue()), y + 16,
				8 + (enableAnimation.getValue() * 4) + (pressAnimation.getValue() * 1), ec);
		Skia.drawCircle(x + 16 + (20 * enableAnimation.getValue()), y + 16,
				8 + (enableAnimation.getValue() * 4) + (pressAnimation.getValue() * 1),
				ColorUtils.applyAlpha(fc, focusAnimation.getValue()));
		Skia.drawFullCenteredText(Icon.CHECK, x + 16 + (20 * enableAnimation.getValue()), y + 16,
				MaterialTheme.alpha(palette.getPrimary(), enableAnimation.getValue()), Fonts.getIcon(14));
		Skia.drawCircle(x + 16 + (20 * enableAnimation.getValue()), y + 16,
				8 + (enableAnimation.getValue() * 4) + (pressAnimation.getValue() * 10),
				ColorUtils.applyAlpha(pc, pressAnimation.getValue() * 0.12F));
	}

	@Override
	public void mousePressed(double mouseX, double mouseY, int button) {
		if (MouseUtils.isInside(mouseX, mouseY, x, y, width, height) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			pressed = true;
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {

		if (MouseUtils.isInside(mouseX, mouseY, x, y, width, height) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			enabled = !enabled;

			if (handler instanceof SwitchHandler) {

				SwitchHandler sHandler = (SwitchHandler) handler;

				if (enabled) {
					sHandler.onEnabled();
				} else {
					sHandler.onDisabled();
				}
			}
		}

		pressed = false;
	}
}
