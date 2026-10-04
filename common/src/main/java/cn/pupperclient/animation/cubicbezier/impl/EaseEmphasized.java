package cn.pupperclient.animation.cubicbezier.impl;

import cn.pupperclient.animation.cubicbezier.CubicBezier;

public class EaseEmphasized extends CubicBezier {

	public EaseEmphasized(float duration, float start, float end) {
		super(0.2F, 0F, 1F, 1F, duration, start, end);
	}
}
