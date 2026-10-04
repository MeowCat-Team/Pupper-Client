package cn.pupperclient.gui.api.page.impl;

import cn.pupperclient.animation.Animation;
import cn.pupperclient.gui.api.page.GuiTransition;

public class RightLeftTransition extends GuiTransition {

	public RightLeftTransition(boolean consecutive) {
		super(consecutive);
	}

	@Override
	public float[] onTransition(Animation animation) {

		float progress = animation.getValue();
		float x = 0;

		if (animation.getEnd() == 1) {
			x = 1 - progress;
		} else {
			x = -1 + progress;
		}

		return new float[] { x, 0 };
	}
}
