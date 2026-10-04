package cn.pupperclient.management.mod.impl.render;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.NumberSetting;
import cn.pupperclient.skia.font.Icon;

public class CustomHandMod extends Mod {

	private static CustomHandMod instance;
	private NumberSetting xSetting = new NumberSetting("setting.x", "setting.x.description", Icon.VIEW_IN_AR, this,
			0.0F, -3.0F, 3.0F, 0.1F);
	private NumberSetting ySetting = new NumberSetting("setting.y", "setting.y.description", Icon.VIEW_IN_AR, this,
			0.0F, -3.0F, 3.0F, 0.1F);
	private NumberSetting zSetting = new NumberSetting("setting.z", "setting.z.description", Icon.VIEW_IN_AR, this,
			0.0F, -3.0F, 3.0F, 0.1F);
	private NumberSetting scaleSetting = new NumberSetting("setting.scale", "setting.scale.description", Icon.ZOOM_IN,
			this, 1.0F, 0.1F, 2.0F, 0.1F);

	public CustomHandMod() {
		super("mod.customhand.name", "mod.customhand.description", Icon.FRONT_HAND, ModCategory.RENDER);

		instance = this;
	}

	public static CustomHandMod getInstance() {
		return instance;
	}
	
	public float getX() {
		return xSetting.getValue() / 5F;
	}
	
	public float getY() {
		return ySetting.getValue() / 5F;
	}
	
	public float getZ() {
		return zSetting.getValue() / 5F;
	}
	
	public float getScale() {
		return scaleSetting.getValue();
	}
}
