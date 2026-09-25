package cn.pupperclient.management.mod.api.hud.design.impl;

import java.awt.Color;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.mod.api.hud.design.HUDDesign;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.utils.color.ColorUtils;

import io.github.humbleui.skija.Font;

public class MaterialYouDesign extends HUDDesign {

	public MaterialYouDesign() {
		super("design.materialyou");
	}

	@Override
	public void drawBackground(float x, float y, float width, float height, float radius) {

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();

		Skia.drawRoundedBlur(x, y, width, height, radius);
		Skia.drawShadow(x, y, width, height, radius);
		Skia.drawGradientRoundedRect(x, y, width, height, radius,
				ColorUtils.applyAlpha(palette.getPrimaryContainer(), 180),
				ColorUtils.applyAlpha(palette.getTertiaryContainer(), 180));
	}

	@Override
	public void drawText(String text, float x, float y, Font font) {
		Skia.drawText(text, x, y, getTextColor(), font);
	}

	@Override
	public Color getTextColor() {
		return PupperClient.getInstance().getColorManager().getPalette().getOnPrimaryContainer();
	}

	@Override
	public Color getOnTextColor() {
		return PupperClient.getInstance().getColorManager().getPalette().getPrimaryContainer();
	}
}
