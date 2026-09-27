package cn.pupperclient.ui.component.impl;

import java.awt.Color;

import org.lwjgl.glfw.GLFW;

import cn.pupperclient.PupperClient;
import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.component.handler.impl.HctColorPickerHandler;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.utils.mouse.MouseUtils;

public class HctColorPicker extends Component {

	private SimpleAnimation slideAnimation = new SimpleAnimation();

	private Hct hct;
	private float minValue, maxValue, value;
	private boolean dragging;

	public HctColorPicker(float x, float y, Hct hct) {
		super(x, y);
		this.hct = hct;
		minValue = 0;
		maxValue = 360;
		value = (float) (hct.getHue() - minValue) / (maxValue - minValue);
		width = 126;
		height = 32;
	}

	@Override
	public void draw(double mouseX, double mouseY) {

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();

		float inset = 12;
		float travelWidth = width - inset * 2;
		slideAnimation.onTick(travelWidth * value, 20);
		float thumbX = x + inset + slideAnimation.getValue();

		drawControlSurface(mouseX, mouseY, MaterialTheme.CONTROL_RADIUS, palette);
		Skia.drawRoundedImage("hue-h.png", x + inset, y + 8, travelWidth, height - 16, 8);
		Skia.drawCircle(thumbX, y + (height / 2), 11, palette.getOnSurface());
		Skia.drawCircle(thumbX, y + (height / 2), 9, palette.getSurface());
		Skia.drawCircle(thumbX, y + (height / 2), 7, new Color(hct.toInt(), true));

		if (dragging) {

			value = (float) Math.min(1, Math.max(0, (mouseX - x - inset) / travelWidth));
			hct = Hct.from((value * (maxValue - minValue) + minValue), hct.getChroma(), hct.getTone());

			onPicking(hct);
		}
	}

	@Override
	public void mousePressed(double mouseX, double mouseY, int button) {

		if (MouseUtils.isInside(mouseX, mouseY, x, y, width, height) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			dragging = true;
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;
	}

	private void onPicking(Hct hct) {
		if (handler instanceof HctColorPickerHandler) {
			((HctColorPickerHandler) handler).onPicking(hct);
		}
	}
}
