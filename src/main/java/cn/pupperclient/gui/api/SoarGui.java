package cn.pupperclient.gui.api;

import java.util.ArrayList;
import java.util.List;

import cn.pupperclient.PupperClient;
import org.lwjgl.glfw.GLFW;

import cn.pupperclient.animation.Animation;
import cn.pupperclient.animation.Duration;
import cn.pupperclient.animation.cubicbezier.impl.EaseEmphasizedDecelerate;
import cn.pupperclient.gui.api.page.GuiTransition;
import cn.pupperclient.gui.api.page.SimplePage;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.config.ConfigType;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.ui.component.Component;

import net.minecraft.client.gui.screens.Screen;

public abstract class SoarGui extends SimpleSoarGui {
	private static final float MIN_SCREEN_SCALE = 0.96F;
	private static final int SCREEN_ANIMATION_DURATION = Duration.MEDIUM_1;
	private static final float SCREEN_CORNER_RADIUS = 28;

	protected List<Component> components = new ArrayList<>();
	protected List<SimplePage> pages;

	protected SimplePage currentPage;
	protected SimplePage lastPage;

	private Animation inOutAnimation;
	private boolean closable;
	private boolean closing;
	private Screen nextScreen;

	public SoarGui() {
		super();

		this.pages = createPages();

		if (!pages.isEmpty()) {
			this.currentPage = pages.getFirst();
		}
	}

	@Override
	public void init() {
		super.init();
		inOutAnimation = new EaseEmphasizedDecelerate(SCREEN_ANIMATION_DURATION, 0, 1);
		closable = true;
		closing = false;
		nextScreen = null;
		lastPage = null;
		if (currentPage != null) {
			setPageSize(currentPage);
			currentPage.init();
		}
	}

	public void setPageSize(SimplePage p) {
		p.setX(getX());
		p.setY(getY());
		p.setWidth(getWidth());
		p.setHeight(getHeight());
	}

	@Override
	public void draw(double mouseX, double mouseY) {

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();
		float animationValue = inOutAnimation.getValue();
		float screenScale = MIN_SCREEN_SCALE + ((1 - MIN_SCREEN_SCALE) * animationValue);
		double contentMouseX = toContentMouseX(mouseX, screenScale);
		double contentMouseY = toContentMouseY(mouseY, screenScale);

		Skia.save();
		Skia.setAlpha((int) (animationValue * 255));
		Skia.scale(getX(), getY(), getWidth(), getHeight(), screenScale);

		Skia.drawShadow(getX(), getY(), getWidth(), getHeight(), SCREEN_CORNER_RADIUS);
		Skia.clip(getX(), getY(), getWidth(), getHeight(), SCREEN_CORNER_RADIUS);
		Skia.drawRoundedRect(getX(), getY(), getWidth(), getHeight(), SCREEN_CORNER_RADIUS,
				palette.getSurfaceContainer());

		if (currentPage != null && lastPage == null) {
			currentPage.draw(contentMouseX, contentMouseY);
		}

		if (currentPage != null && lastPage != null) {

			GuiTransition currentTransition = currentPage.getTransition();
			GuiTransition lastTransition = lastPage.getTransition();

			if (currentTransition != null && currentTransition.isConsecutive()) {

				Skia.save();
				float[] offset = getTransitionOffset(lastTransition, lastPage);
				float offsetX = offset[0] * getWidth();
				float offsetY = offset[1] * getHeight();
				Skia.translate(offsetX, offsetY);
				lastPage.draw(contentMouseX - offsetX, contentMouseY - offsetY);
				Skia.restore();
			}

			Skia.save();
			float[] offset = getTransitionOffset(currentTransition, currentPage);
			float offsetX = offset[0] * getWidth();
			float offsetY = offset[1] * getHeight();
			Skia.translate(offsetX, offsetY);
			currentPage.draw(contentMouseX - offsetX, contentMouseY - offsetY);
			Skia.restore();

			if (lastPage.getAnimation().isFinished()) {
				lastPage = null;
			}
		}

		for (Component c : components) {
			c.draw(contentMouseX, contentMouseY);
		}

		Skia.restore();
		Skia.restore();

	}

	@Override
	public void tick() {
		super.tick();
		if (closing && inOutAnimation.isFinished()) {
			Screen target = nextScreen;
			closing = false;
			nextScreen = null;
			if (client.screen == this) {
				client.setScreen(target);
			}
		}
	}

	@Override
	public boolean onMousePressed(double mouseX, double mouseY, int button, boolean doubled) {
		if (closing) {
			return true;
		}

		float screenScale = getScreenScale();
		double contentMouseX = toContentMouseX(mouseX, screenScale);
		double contentMouseY = toContentMouseY(mouseY, screenScale);
		double[] pageMouse = toCurrentPageMouse(contentMouseX, contentMouseY);

		if (currentPage != null) {
			currentPage.mousePressed(pageMouse[0], pageMouse[1], button);
		}

		for (Component c : components) {
			c.mousePressed(contentMouseX, contentMouseY, button);
		}
        return true;
	}

	@Override
	public boolean onMouseReleased(double mouseX, double mouseY, int button) {
		if (closing) {
			return true;
		}

		float screenScale = getScreenScale();
		double contentMouseX = toContentMouseX(mouseX, screenScale);
		double contentMouseY = toContentMouseY(mouseY, screenScale);
		double[] pageMouse = toCurrentPageMouse(contentMouseX, contentMouseY);

		if (currentPage != null) {
			currentPage.mouseReleased(pageMouse[0], pageMouse[1], button);
		}

		for (Component c : components) {
			c.mouseReleased(contentMouseX, contentMouseY, button);
		}
        return true;
	}

	@Override
	public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (closing) {
			return true;
		}

		float screenScale = getScreenScale();
		double contentMouseX = toContentMouseX(mouseX, screenScale);
		double contentMouseY = toContentMouseY(mouseY, screenScale);
		double[] pageMouse = toCurrentPageMouse(contentMouseX, contentMouseY);

		if (currentPage != null) {
			currentPage.mouseScrolled(pageMouse[0], pageMouse[1], horizontalAmount, verticalAmount);
		}

		for (Component c : components) {
			c.mouseScrolled(contentMouseX, contentMouseY, horizontalAmount, verticalAmount);
		}
		return true;
	}

	@Override
	public boolean onCharTyped(int chr) {

		if (currentPage != null) {
			currentPage.charTyped(chr);
		}

		for (Component c : components) {
			c.charTyped(chr);
		}
        return true;
	}

	@Override
	public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {

		if (keyCode == GLFW.GLFW_KEY_ESCAPE && closable) {
			onClose();
			return true;
		}

		if (currentPage != null) {
			currentPage.keyPressed(keyCode, scanCode, modifiers);
		}

		for (Component c : components) {
			c.keyPressed(keyCode, scanCode, modifiers);
		}
        return true;
	}

	public void close(Screen nextScreen) {
		if (!closing && inOutAnimation.getEnd() == 1) {
			this.nextScreen = nextScreen;
			closing = true;
			inOutAnimation = new EaseEmphasizedDecelerate(SCREEN_ANIMATION_DURATION, 1, 0);
			client.execute(() -> {
				PupperClient.getInstance().getConfigManager().save(ConfigType.MOD);
			});
		}
	}

	public void close() {
		close(null);
	}

	@Override
	public void onClose() {
		if (closable) {
			close();
		}
	}

	public SimplePage getCurrentPage() {
		return currentPage;
	}

	public void setCurrentPage(SimplePage page) {
		if (page == null || page == currentPage) {
			return;
		}

		if (currentPage == null) {
			currentPage = page;
			setPageSize(currentPage);
			currentPage.init();
			return;
		}

		lastPage = currentPage;
		currentPage.onClosed();
		this.currentPage = page;
		currentPage.setAnimation(new EaseEmphasizedDecelerate(Duration.MEDIUM_1, 0, 1));
		lastPage.setAnimation(new EaseEmphasizedDecelerate(Duration.MEDIUM_1, 1, 0));

		setPageSize(currentPage);
		currentPage.init();
	}

	public void setCurrentPage(Class<? extends SimplePage> clazz) {

		SimplePage page = getPage(clazz);

		if (page != null) {
			setCurrentPage(page);
		}
	}

	public SimplePage getPage(Class<? extends SimplePage> clazz) {

		SimplePage page = null;

		for (SimplePage p : pages) {
			if (p.getClass().equals(clazz)) {
				page = p;
				break;
			}
		}

		return page;
	}

	public List<SimplePage> getPages() {
		return pages;
	}

	public boolean isClosable() {
		return closable;
	}

	public void setClosable(boolean closable) {
		this.closable = closable;
	}

	private float getScreenScale() {
		if (inOutAnimation == null) {
			return 1;
		}
		float animationValue = inOutAnimation.getValue();
		return MIN_SCREEN_SCALE + ((1 - MIN_SCREEN_SCALE) * animationValue);
	}

	private double toContentMouseX(double mouseX, float screenScale) {
		double centerX = getX() + getWidth() / 2.0;
		return centerX + ((mouseX - centerX) / screenScale);
	}

	private double toContentMouseY(double mouseY, float screenScale) {
		double centerY = getY() + getHeight() / 2.0;
		return centerY + ((mouseY - centerY) / screenScale);
	}

	private double[] toCurrentPageMouse(double mouseX, double mouseY) {
		if (currentPage == null || lastPage == null) {
			return new double[] { mouseX, mouseY };
		}
		float[] offset = getTransitionOffset(currentPage.getTransition(), currentPage);
		return new double[] {
				mouseX - offset[0] * getWidth(),
				mouseY - offset[1] * getHeight()
		};
	}

	private float[] getTransitionOffset(GuiTransition transition, SimplePage page) {
		return transition == null ? new float[] { 0, 0 } : transition.onTransition(page.getAnimation());
	}

	public abstract List<SimplePage> createPages();

	public abstract float getX();

	public abstract float getY();

	public abstract float getWidth();

	public abstract float getHeight();
}
