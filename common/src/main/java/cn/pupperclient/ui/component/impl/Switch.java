package cn.pupperclient.ui.component.impl;

import org.lwjgl.glfw.GLFW;

import cn.pupperclient.PupperClient;
import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.component.handler.impl.SwitchHandler;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.ui.theme.MaterialControls;
import cn.pupperclient.utils.mouse.MouseUtils;

public class Switch extends Component {

	private SimpleAnimation enableAnimation = new SimpleAnimation();
	private SimpleAnimation pressAnimation = new SimpleAnimation();
	private SimpleAnimation focusAnimation = new SimpleAnimation();
	private boolean pressed;
	private boolean enabled;

	public Switch(float x, float y, boolean enabled) {
		super(x, y);
		this.width = MaterialControls.SWITCH_WIDTH;
		this.height = MaterialControls.SWITCH_HEIGHT;
		this.enabled = enabled;
		this.pressed = false;
	}

	@Override
	public void draw(double mouseX, double mouseY) {

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();
		boolean focus = MouseUtils.isInside(mouseX, mouseY, x, y, width, height);

		enableAnimation.onTick(enabled ? 1 : 0, 12);
		pressAnimation.onTick(pressed ? 1 : 0, 12);
		focusAnimation.onTick(focus ? 1 : 0, 10);

		MaterialControls.switchControl(x, y, palette, MaterialTheme.opacity(), enableAnimation.getValue(),
				focusAnimation.getValue(), pressAnimation.getValue());
	}

	@Override
	public void mousePressed(double mouseX, double mouseY, int button) {
		if (MouseUtils.isInside(mouseX, mouseY, x, y, width, height) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			pressed = true;
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {

		if (pressed && MouseUtils.isInside(mouseX, mouseY, x, y, width, height) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
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
