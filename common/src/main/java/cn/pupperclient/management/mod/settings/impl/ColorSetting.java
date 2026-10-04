package cn.pupperclient.management.mod.settings.impl;

import java.awt.Color;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.settings.Setting;
import cn.pupperclient.utils.color.ColorUtils;

public class ColorSetting extends Setting {

	private Color defaultColor;
	private float hue;
	private float saturation;
	private float brightness;
	private float alpha;
	private boolean showAlpha;

	public ColorSetting(String name, String description, String icon, Mod parent, Color color, boolean showAlpha) {
		super(name, description, icon, parent);

		this.defaultColor = color;
		this.showAlpha = showAlpha;
		this.alpha = color.getAlpha() / 255F;
		this.setColor(color);

		PupperClient.getInstance().getModManager().addSetting(this);
	}

	@Override
	public void reset() {
		setColor(defaultColor);
	}

	public Color getColor() {
		return ColorUtils.applyAlpha(Color.getHSBColor(hue, saturation, brightness), showAlpha ? alpha : 1F);
	}

	public void setColor(Color color) {

		float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);

		hue = hsb[0];
		saturation = hsb[1];
		brightness = hsb[2];
		alpha = color.getAlpha() / 255F;
	}

	public boolean isShowAlpha() {
		return showAlpha;
	}

	public void setShowAlpha(boolean showAlpha) {
		this.showAlpha = showAlpha;
	}

	public void setAlpha(float alpha) {
		this.alpha = alpha;
	}
}
