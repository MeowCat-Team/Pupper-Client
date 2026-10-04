package cn.pupperclient.gui.api.page;

import cn.pupperclient.animation.Animation;

public abstract class GuiTransition {

	private final boolean consecutive;

	public GuiTransition(boolean consecutive) {
		this.consecutive = consecutive;
	}

	public abstract float[] onTransition(Animation animation);

	public boolean isConsecutive() {
		return consecutive;
	}
}
