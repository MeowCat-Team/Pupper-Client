package cn.pupperclient.management.mod.impl.settings;

import java.util.Arrays;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.mod.api.hud.design.HUDColors;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.management.mod.settings.impl.ComboSetting;
import cn.pupperclient.management.mod.settings.impl.NumberSetting;
import cn.pupperclient.skia.font.Icon;

public class HUDModSettings extends Mod {

	private static HUDModSettings instance;
	private final NumberSetting backgroundOpacitySetting = new NumberSetting("setting.hud.backgroundopacity",
			"setting.hud.backgroundopacity.description", Icon.PALETTE, this, HUDColors.DEFAULT_OPACITY * 100, HUDColors.MIN_OPACITY * 100, 100, 1);
	private final BooleanSetting backgroundBlurSetting = new BooleanSetting("setting.hud.backgroundblur",
			"setting.hud.backgroundblur.description", Icon.BLUR_ON, this, false);

	private final BooleanSetting reducedMotionSetting = new BooleanSetting("setting.hud.reducedmotion",
			"setting.hud.reducedmotion.description", Icon.MOVIE, this, false);
	private ComboSetting designSetting = new ComboSetting("setting.design", "setting.design.description", Icon.PALETTE,
			this, Arrays.asList("design.simple", "design.classic", "design.clear", "design.materialyou", "design.glass"),
			"design.materialyou");

	public HUDModSettings() {
		super("mod.hudsettings.name", "mod.hudsettings.description", Icon.BROWSE_ACTIVITY, ModCategory.MISC);
		this.setHidden(true);
		this.setEnabled(true);

		instance = this;
	}

	public final EventBus.EventListener<ClientTickEvent> onClientTick = event -> {
		if (!designSetting.getOption().equals(PupperClient.getInstance().getModManager().getCurrentDesign().getName())) {
			PupperClient.getInstance().getModManager().setCurrentDesign(designSetting.getOption());
		}
	};

	@Override
	public void onDisable() {
		this.setEnabled(true);
	}

	public static HUDModSettings getInstance() {
		return instance;
	}

	public BooleanSetting getReducedMotionSetting() { return reducedMotionSetting; }
	public BooleanSetting getBackgroundBlurSetting() { return backgroundBlurSetting; }
	public float getBackgroundOpacity() { return backgroundOpacitySetting.getValue() / 100f; }
}
