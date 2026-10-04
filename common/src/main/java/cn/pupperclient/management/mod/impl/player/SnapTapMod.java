package cn.pupperclient.management.mod.impl.player;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.skia.font.Icon;

public class SnapTapMod extends Mod {

	private static SnapTapMod instance;
	private long leftPressTime, rightPressTime, forwardPressTime, backPressTime;

	public SnapTapMod() {
		super("mod.snaptap.name", "mod.snaptap.description", Icon.KEYBOARD_KEYS, ModCategory.PLAYER);
		instance = this;
	}

	public static SnapTapMod getInstance() {
		return instance;
	}

	public long getLeftPressTime() {
		return leftPressTime;
	}

	public void setLeftPressTime(long leftPressTime) {
		this.leftPressTime = leftPressTime;
	}

	public long getRightPressTime() {
		return rightPressTime;
	}

	public void setRightPressTime(long rightPressTime) {
		this.rightPressTime = rightPressTime;
	}

	public long getForwardPressTime() {
		return forwardPressTime;
	}

	public void setForwardPressTime(long forwardPressTime) {
		this.forwardPressTime = forwardPressTime;
	}

	public long getBackPressTime() {
		return backPressTime;
	}

	public void setBackPressTime(long backPressTime) {
		this.backPressTime = backPressTime;
	}
}
