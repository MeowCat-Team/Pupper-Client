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
import net.minecraft.util.Util;

public abstract class SoarGui extends SimpleSoarGui {

	protected List<Component> components = new ArrayList<>();
	protected List<SimplePage> pages;

	protected SimplePage currentPage;
	protected SimplePage lastPage;

	private Animation inOutAnimation;
	private boolean closable;
	private boolean closing;
	private long closeStartedAt;
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
		setPageSize(currentPage);
		inOutAnimation = new EaseEmphasizedDecelerate(Duration.EXTRA_LONG_1, 0, 1);
		closable = true;
		closing = false;
		closeStartedAt = 0L;
		nextScreen = null;
		currentPage.init();
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

		Skia.save();
		Skia.setAlpha((int) (animationValue * 255));
		Skia.scale(getX(), getY(), getWidth(), getHeight(), 2 - animationValue);

		Skia.clip(getX(), getY(), getWidth(), getHeight(), 35);
		Skia.drawRoundedRect(getX(), getY(), getWidth(), getHeight(), 35, palette.getSurfaceContainer());

		if (currentPage != null && lastPage == null) {
			currentPage.draw(mouseX, mouseY);
		}

		if (lastPage != null) {

			GuiTransition transition = lastPage.getTransition();

			if (currentPage.getTransition().isConsecutive()) {

				Skia.save();

				if (transition != null) {
					float[] result = transition.onTransition(lastPage.getAnimation());
					Skia.translate(result[0] * getWidth(), result[1] * getHeight());
				}

				lastPage.draw(mouseX, mouseY);
				Skia.restore();
			}

			Skia.save();
			transition = currentPage.getTransition();

			if (transition != null) {
				float[] result = transition.onTransition(currentPage.getAnimation());
				Skia.translate(result[0] * getWidth(), result[1] * getHeight());
			}

			currentPage.draw(mouseX, mouseY);
			Skia.restore();

			if (lastPage.getAnimation().isFinished()) {
				lastPage = null;
			}
		}

		for (Component c : components) {
			c.draw(mouseX, mouseY);
		}

		Skia.restore();
		Skia.restore();

	}

	@Override
	public void tick() {
		super.tick();
		if (closing && Util.getMillis() - closeStartedAt >= Duration.EXTRA_LONG_1) {
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

		if (currentPage != null) {
			currentPage.mousePressed(mouseX, mouseY, button);
		}

		for (Component c : components) {
			c.mousePressed(mouseX, mouseY, button);
		}
        return true;
	}

	@Override
	public boolean onMouseReleased(double mouseX, double mouseY, int button) {

		if (currentPage != null) {
			currentPage.mouseReleased(mouseX, mouseY, button);
		}

		for (Component c : components) {
			c.mouseReleased(mouseX, mouseY, button);
		}
        return true;
	}

	@Override
	public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (currentPage != null) {
			currentPage.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}

		for (Component c : components) {
			c.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
			closeStartedAt = Util.getMillis();
			inOutAnimation = new EaseEmphasizedDecelerate(Duration.EXTRA_LONG_1, 1, 0);
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

		if (currentPage != null) {
			lastPage = currentPage;
			currentPage.onClosed();
		}

		this.currentPage = page;
		currentPage.setAnimation(new EaseEmphasizedDecelerate(Duration.MEDIUM_1, 0, 1));
		lastPage.setAnimation(new EaseEmphasizedDecelerate(Duration.MEDIUM_1, 1, 0));

		if (currentPage != null) {
			setPageSize(currentPage);
			currentPage.init();
		}
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

	public abstract List<SimplePage> createPages();

	public abstract float getX();

	public abstract float getY();

	public abstract float getWidth();

	public abstract float getHeight();
}
