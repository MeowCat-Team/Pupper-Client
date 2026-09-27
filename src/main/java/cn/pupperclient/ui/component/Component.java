package cn.pupperclient.ui.component;

import java.awt.Color;

import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.ui.component.handler.ComponentHandler;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.ui.theme.MaterialControls;
import cn.pupperclient.utils.mouse.MouseUtils;
import net.minecraft.client.Minecraft;

public class Component {

	protected Minecraft client = Minecraft.getInstance();
	protected ComponentHandler handler;
	protected float x, y, width, height;
	private final SimpleAnimation hoverAnimation = new SimpleAnimation();

	public Component(float x, float y) {
		this.x = x;
		this.y = y;
		this.handler = new ComponentHandler() {
		};
	}

	public void draw(double mouseX, double mouseY) {
	}

	/** Shared glass surface and Material state layer for compact controls. */
	protected void drawControlSurface(double mouseX, double mouseY, float radius, ColorPalette palette) {
		MaterialControls.surface(x, y, width, height, radius, palette, MaterialTheme.opacity(), hoverState(mouseX, mouseY), 0);
	}

	protected float hoverState(double mouseX, double mouseY) {
		hoverAnimation.onTick(MouseUtils.isInside(mouseX, mouseY, x, y, width, height) ? 1 : 0, 12);
		return hoverAnimation.getValue();
	}

	protected void drawHoverState(double mouseX, double mouseY, float radius, Color color) {
		Skia.drawRoundedRect(x, y, width, height, radius,
				MaterialTheme.alpha(color, hoverState(mouseX, mouseY) * 0.08F));
	}

	public void mousePressed(double mouseX, double mouseY, int button) {
	}

	public void mouseReleased(double mouseX, double mouseY, int button) {
	}

	public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
	}

	public void charTyped(int chr) {
	}

	public void keyPressed(int keyCode, int scanCode, int modifiers) {
	}

	public float getX() {
		return x;
	}

	public void setX(float x) {
		this.x = x;
	}

	public float getY() {
		return y;
	}

	public void setY(float y) {
		this.y = y;
	}

	public float getWidth() {
		return width;
	}

	public float getHeight() {
		return height;
	}

	public ComponentHandler getHandler() {
		return handler;
	}

	public void setHandler(ComponentHandler handler) {
		this.handler = handler;
	}
}
