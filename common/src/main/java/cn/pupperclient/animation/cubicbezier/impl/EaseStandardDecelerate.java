package cn.pupperclient.animation.cubicbezier.impl;

import cn.pupperclient.animation.cubicbezier.CubicBezier;

public class EaseStandardDecelerate extends CubicBezier {

	public EaseStandardDecelerate(float duration, float start, float end) {
		super(0F, 0F, 0F, 1F, duration, start, end);
	}
}
