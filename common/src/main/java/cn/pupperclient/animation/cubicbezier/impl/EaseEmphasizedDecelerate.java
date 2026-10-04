package cn.pupperclient.animation.cubicbezier.impl;

import cn.pupperclient.animation.cubicbezier.CubicBezier;

public class EaseEmphasizedDecelerate extends CubicBezier {

	public EaseEmphasizedDecelerate(float duration, float start, float end) {
		super(0.05F, 0.7F, 0.1F, 1F, duration, start, end);
	}
}
