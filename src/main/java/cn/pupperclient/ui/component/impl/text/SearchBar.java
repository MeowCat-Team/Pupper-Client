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
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.ui.theme.MaterialControls;
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
		this.height = MaterialControls.SEARCH_HEIGHT;
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

		focusAnimation.onTick(isFocused() ? 1 : 0, 12);
		updateCursor();
		MaterialControls.textInput(x, y, width, height, true, palette, MaterialTheme.opacity(),
				hoverState(mouseX, mouseY), focusAnimation.getValue(),
				new MaterialControls.TextState(getText(), cursorAnimation.getValue(), cursorFlashAnimation.getValue(),
						input.getCursorPosition(), input.getSelectionEnd(), I18n.get(hintText), hintTextAnimation.getValue()));
	}

	private void updateCursor() {

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

		float cursorOffset = Skia.getTextBounds(text.substring(0, cursorPosition), Fonts.getRegular(16)).getWidth();
		cursorAnimation.onTick(cursorOffset, 16);
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
