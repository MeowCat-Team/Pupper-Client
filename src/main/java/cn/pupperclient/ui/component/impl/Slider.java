package cn.pupperclient.ui.component.impl;

import cn.pupperclient.PupperClient;
import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.component.handler.impl.SliderHandler;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.utils.color.ColorUtils;
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
		this.height = 38;
		this.minValue = minValue;
		this.maxValue = maxValue;
		this.step = step;
		this.setValue(value);
	}

	@Override
	public void draw(double mouseX, double mouseY) {

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();

		slideAnimation.onTick(((getValue() - minValue) / (maxValue - minValue)) * width, 20);

		float padding = 6;
		float selWidth = 4;
		float barHeight = 16;
		float offsetY = (height / 2) - (barHeight / 2);

		float slideValue = Math.max(0, Math.min(width, slideAnimation.getValue()));
		boolean focus = MouseUtils.isInside(mouseX, mouseY, x, y, width, height);
		valueAnimation.onTick(focus || dragging ? 1 : 0, 16);

		Skia.drawCircle(x + slideValue, y + height / 2, 20,
				MaterialTheme.alpha(palette.getPrimary(), valueAnimation.getValue() * 0.08F));
		Skia.drawRoundedRect(x + slideValue - (selWidth / 2), y, selWidth, height, 3, palette.getPrimary());

		Skia.save();
		Skia.clip(x, y, width, height, 0);
		float activeWidth = Math.max(0, slideValue - (selWidth / 2) - padding);
		float remainingStart = slideValue + padding + (selWidth / 2);
		float remainingWidth = Math.max(0, width - remainingStart);
		if (activeWidth > 0) {
			Skia.drawRoundedRectVarying(x, y + offsetY, activeWidth, barHeight, 8, 4, 4, 8,
					palette.getPrimary());
		}
		if (remainingWidth > 0) {
			Skia.drawRoundedRectVarying(x + remainingStart, y + offsetY,
					remainingWidth, barHeight, 4, 8, 8, 4, MaterialTheme.surface(palette.getPrimaryContainer()));
		}
		Skia.restore();

		String valueText = String.valueOf(getValue());
		float pWidth = Math.max(38, Skia.getTextBounds(valueText, Fonts.getMedium(12)).getWidth() + 20);
		float centerX = Math.max(x + pWidth / 2, Math.min(x + width - pWidth / 2, x + slideValue));
		float pHeight = 28;

		Skia.save();
		Skia.translate(0, 10 - (valueAnimation.getValue() * 10));
		Skia.drawRoundedRect(centerX - (pWidth / 2), y - pHeight - 6, pWidth, pHeight, pHeight / 2,
				ColorUtils.applyAlpha(palette.getPrimary(), valueAnimation.getValue()));
		Skia.drawFullCenteredText(valueText, centerX,
				y - (pHeight / 2) - 6, ColorUtils.applyAlpha(palette.getOnPrimary(), valueAnimation.getValue()),
				Fonts.getMedium(12));
		Skia.restore();

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
