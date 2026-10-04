package cn.pupperclient.animation.cubicbezier.impl;

import cn.pupperclient.animation.cubicbezier.CubicBezier;

public class EaseStandard extends CubicBezier {

	public EaseStandard(float duration, float start, float end) {
		super(0.2F, 0.0F, 0F, 1F, duration, start, end);
	}
}
