package cn.pupperclient.ui.component.impl.text;

import cn.pupperclient.PupperClient;
import org.lwjgl.glfw.GLFW;

import cn.pupperclient.animation.Animation;
import cn.pupperclient.animation.Duration;
import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.animation.cubicbezier.impl.EaseStandard;
import cn.pupperclient.animation.other.DummyAnimation;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.utils.color.ColorUtils;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.mouse.MouseUtils;

public class SearchBar extends Component {

	private Runnable shortcutEvent;

	private SimpleAnimation cursorAnimation = new SimpleAnimation();
	private SimpleAnimation focusAnimation = new SimpleAnimation();
	private Animation cursorFlashAnimation;
	private Animation hintTextAnimation;

	private TextInputHelper input = new TextInputHelper();
	private String hintText;

	public SearchBar(float x, float y, float width, String text, Runnable shortcutEvent) {
		super(x, y);
		this.shortcutEvent = shortcutEvent;
		this.width = width;
		this.height = 42;
		this.setText(text);

		if (getText().isBlank()) {
			this.hintTextAnimation = new DummyAnimation(1);
		} else {
			this.hintTextAnimation = new DummyAnimation(0);
		}

		this.cursorFlashAnimation = new DummyAnimation(0, 0);
		this.hintText = "text.search";
	}

	@Override
	public void draw(double mouseX, double mouseY) {

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();

		float hintTextValue = hintTextAnimation.getValue();

		drawControlSurface(mouseX, mouseY, height / 2, palette);
		focusAnimation.onTick(isFocused() ? 1 : 0, 12);
		Skia.drawOutline(x, y, width, height, height / 2, 2,
				MaterialTheme.alpha(palette.getPrimary(), focusAnimation.getValue()));
		Skia.drawHeightCenteredText(Icon.SEARCH, x + 12, y + (height / 2), palette.getPrimary(), Fonts.getIcon(24));
		Skia.save();
		Skia.clip(x + 40, y + 4, width - 52, height - 8, 0);
		Skia.drawHeightCenteredText(I18n.get(hintText), x + 40 - (25 * (1 - hintTextValue)), y + (height / 2),
				ColorUtils.applyAlpha(palette.getOnSurfaceVariant(), (int) (hintTextValue * 255)),
				Fonts.getRegular(16));

		drawCursor();

		String text = getText();

		if (!text.isEmpty() || isFocused()) {
			float availableWidth = width - 54;
			float textWidth = Skia.getTextBounds(text, Fonts.getRegular(16)).getWidth();

			float xOffset = 0;
			if (textWidth > availableWidth) {
				float overflow = textWidth - availableWidth;
				xOffset = -overflow;
			}

			Skia.drawHeightCenteredText(text, x + 40 + xOffset, y + (height / 2), palette.getOnSurface(),
					Fonts.getRegular(16));
		}

		Skia.restore();
	}

	private void drawCursor() {

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();

		int selectionEnd = input.getSelectionEnd();
		int cursorPosition = input.getCursorPosition();
		String text = getText();
		boolean focused = isFocused();

		if (focused && cursorPosition == selectionEnd) {
			if (cursorFlashAnimation.getEnd() == 0 && cursorFlashAnimation.isFinished()) {
				cursorFlashAnimation = new EaseStandard(Duration.SHORT_4, 0, 1);
			} else if (cursorFlashAnimation.getEnd() == 1 && cursorFlashAnimation.isFinished()) {
				cursorFlashAnimation = new EaseStandard(Duration.SHORT_4, 1, 0);
			}
		} else {
			if (cursorFlashAnimation.getEnd() != 0) {
				cursorFlashAnimation = new EaseStandard(Duration.SHORT_4, 1, 0);
			}
		}

		float textWidth = Skia.getTextBounds(text, Fonts.getRegular(16)).getWidth();
		float availableWidth = width - 54;
		float xOffset = 0;

		if (textWidth > availableWidth) {
			float overflow = textWidth - availableWidth;
			xOffset = -overflow;
		}

		float cursorOffset = Skia.getTextBounds(text.substring(0, cursorPosition), Fonts.getRegular(16)).getWidth();
		cursorAnimation.onTick(cursorOffset, 16);
		float cursorX = x + 40 + cursorAnimation.getValue() + xOffset;

		Skia.drawRect(cursorX, y + 9, 1, 24,
				ColorUtils.applyAlpha(palette.getSurfaceTint(), (int) (cursorFlashAnimation.getValue() * 255)));

		if (cursorPosition != selectionEnd) {
			int start = Math.min(cursorPosition, selectionEnd);
			int end = Math.max(cursorPosition, selectionEnd);

			float selectionWidth = Skia.getTextBounds(text.substring(start, end), Fonts.getRegular(16)).getWidth();
			float selectionOffset = Skia.getTextBounds(text.substring(0, start), Fonts.getRegular(16)).getWidth();

			Skia.drawRoundedRect(x + 40 + selectionOffset + xOffset, y + 9, selectionWidth, 24, 4,
					MaterialTheme.alpha(palette.getPrimary(), 0.26F));
		}
	}

	@Override
	public void mousePressed(double mouseX, double mouseY, int button) {

		boolean isInside = MouseUtils.isInside(mouseX, mouseY, x, y, width, height);

		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {

			if (isInside && !isFocused()) {

				if (getText().isEmpty()) {
					hintTextAnimation = new EaseStandard(Duration.MEDIUM_1, hintTextAnimation.getValue(), 0);
				}

				input.setFocused(true);
			} else if (!isInside && isFocused()) {

				if (getText().isEmpty()) {
					hintTextAnimation = new EaseStandard(Duration.MEDIUM_1, hintTextAnimation.getValue(), 1);
				}

				input.setFocused(false);
			}
		}
	}

	@Override
	public void keyPressed(int keyCode, int scanCode, int modifiers) {

		if (modifiers == GLFW.GLFW_MOD_CONTROL && keyCode == GLFW.GLFW_KEY_F && !isFocused()) {

			if (getText().isEmpty()) {
				hintTextAnimation = new EaseStandard(Duration.MEDIUM_1, hintTextAnimation.getValue(), 0);
			}

			shortcutEvent.run();
			input.setFocused(true);
		} else {
			input.keyPressed(keyCode, scanCode, modifiers);
		}
	}

	@Override
	public void charTyped(int chr) {
		input.charTyped(chr);
	}

	public String getHintText() {
		return hintText;
	}

	public void setHintText(String hintText) {
		this.hintText = hintText;
	}

	public String getText() {
		return input.getText();
	}

	public void setText(String text) {
		input.setText(text);
	}

	public boolean isFocused() {
		return input.isFocused();
	}
}
