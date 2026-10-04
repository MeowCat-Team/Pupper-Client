package cn.pupperclient.ui.component.impl;

import cn.pupperclient.PupperClient;
import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.component.handler.impl.SliderHandler;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.ui.theme.MaterialControls;
import cn.pupperclient.utils.math.MathUtils;
import cn.pupperclient.utils.mouse.MouseUtils;

public class Slider extends Component {

	private SimpleAnimation slideAnimation = new SimpleAnimation();
	private SimpleAnimation valueAnimation = new SimpleAnimation();

	private boolean dragging;
	private float value, minValue, maxValue;
	private float step;

	public Slider(float x, float y, float width, float value, float minValue, float maxValue, float step) {
		super(x, y);
		this.width = width;
		this.height = MaterialControls.SLIDER_HEIGHT;
		this.minValue = minValue;
		this.maxValue = maxValue;
		this.step = step;
		this.setValue(value);
	}

	@Override
	public void draw(double mouseX, double mouseY) {

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();

		slideAnimation.onTick(value, 20);
		boolean focus = MouseUtils.isInside(mouseX, mouseY, x, y, width, height);
		valueAnimation.onTick(focus || dragging ? 1 : 0, 16);

		MaterialControls.slider(x, y, width, palette, MaterialTheme.opacity(), slideAnimation.getValue(),
				valueAnimation.getValue(), String.valueOf(getValue()));

		if (dragging) {

			float rawValue = (float) Math.min(1, Math.max(0, (mouseX - x) / width));
			float actualValue = rawValue * (maxValue - minValue) + minValue;
			float steppedValue = Math.round(actualValue / step) * step;

			value = Math.max(0, Math.min(1, (steppedValue - minValue) / (maxValue - minValue)));

			if (handler instanceof SliderHandler) {
				((SliderHandler) handler).onValueChanged(getValue());
			}
		}
	}

	@Override
	public void mousePressed(double mouseX, double mouseY, int button) {
		if (MouseUtils.isInside(mouseX, mouseY, x, y, width, height) && button == 0) {
			dragging = true;
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		if (dragging) {
			dragging = false;
		}
	}

	public float getValue() {
		return MathUtils.roundToPlace(value * (maxValue - minValue) + minValue, 2);
	}

	public void setValue(float value) {
		float steppedValue = Math.round(value / step) * step;
		this.value = Math.max(0, Math.min(1, (steppedValue - minValue) / (maxValue - minValue)));
	}
}
