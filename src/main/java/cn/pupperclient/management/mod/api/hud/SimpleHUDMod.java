package cn.pupperclient.management.mod.api.hud;

import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;

import io.github.humbleui.skija.Font;
import io.github.humbleui.types.Rect;

public abstract class SimpleHUDMod extends HUDMod {

	protected BooleanSetting iconSetting = new BooleanSetting("setting.icon", "setting.icon.description",
			Icon.NEW_RELEASES, this, true);

	public SimpleHUDMod(String name, String description, String icon) {
		super(name, description, icon);
	}

	protected void draw() {

		float fontSize = 9;
		float iconSize = 10.5F;
		float padding = 5;
		String text = getText();
		String icon = getIcon();
		Font textFont = getTextFont(fontSize);
		Font iconFont = getIconFont(iconSize);
		boolean hasIcon = icon != null && !icon.isBlank() && iconSetting.isEnabled();
		Rect textBounds = Skia.getTextBounds(text, textFont);
		Rect iconBounds = hasIcon ? Skia.getTextBounds(icon, iconFont) : Rect.makeWH(0, 0);
		float width = textBounds.getWidth() + (padding * 2) + (hasIcon ? iconBounds.getWidth() + 4 : 0);
		float height = fontSize + (padding * 2) - 1.5F;

		this.begin();
		this.drawBackground(getX(), getY(), width, height);

		if (hasIcon) {
			Skia.drawFullCenteredText(icon, getX() + padding + iconBounds.getWidth() / 2,
				getY() + height / 2, getDesign().getTextColor(), iconFont);
		}

		Skia.drawFullCenteredText(text,
			getX() + padding + (hasIcon ? iconBounds.getWidth() + 4 : 0) + textBounds.getWidth() / 2,
			getY() + height / 2, getDesign().getTextColor(), textFont);
		this.finish();

		position.setSize(width, height);
	}

    @Override
	public float getRadius() {
		return 6;
	}

	public abstract String getText();

	public abstract String getIcon();

	protected Font getTextFont(float size) {
		return Fonts.getRegular(size);
	}

	protected Font getIconFont(float size) {
		return Fonts.getIcon(size);
	}
}
