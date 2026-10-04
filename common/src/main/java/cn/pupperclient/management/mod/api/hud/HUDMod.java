package cn.pupperclient.management.mod.api.hud;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.api.Position;
import cn.pupperclient.management.mod.api.hud.design.HUDDesign;
import cn.pupperclient.management.mod.api.hud.design.HUDColors;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.management.mod.impl.settings.HUDModSettings;
import cn.pupperclient.skia.Skia;

import io.github.humbleui.skija.Font;

public abstract class HUDMod extends Mod {

	protected Position position;

	public HUDMod(String name, String description, String icon) {
		super(name, description, icon, ModCategory.HUD);
		this.position = new Position(100, 100, 0, 0);
		this.setMovable(true);
	}

	public void begin() {
		Skia.save();
		Skia.scale(position.getX(), position.getY(), position.getScale());
	}

	public void finish() {
		Skia.restore();
	}

	public void drawBackground(float x, float y, float width, float height) {
		getDesign().drawBackground(x, y, width, height, getRadius());
	}
	

	public void drawText(String text, float x, float y, Font font) {
		getDesign().drawText(text, x, y, font);
	}

	public float getRadius() { return HUDTokens.RADIUS; }

	public HUDColors colors() { return getDesign().colors(); }

	protected boolean reducedMotion() {
		return HUDModSettings.getInstance().getReducedMotionSetting().isEnabled();
	}

	public HUDDesign getDesign() {
		return PupperClient.getInstance().getModManager().getCurrentDesign();
	}

	public Position getPosition() {
		return position;
	}

	public float getX() {
		return position.getX();
	}

	public float getY() {
		return position.getY();
	}
}
