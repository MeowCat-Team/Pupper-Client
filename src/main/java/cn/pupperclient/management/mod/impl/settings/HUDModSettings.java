package cn.pupperclient.management.mod.impl.settings;

import java.util.Arrays;

import cn.pupperclient.PupperClient;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.management.mod.settings.impl.ComboSetting;
import cn.pupperclient.skia.font.Icon;

public class HUDModSettings extends Mod {

	private static HUDModSettings instance;

	private final BooleanSetting reducedMotionSetting = new BooleanSetting("setting.hud.reducedmotion",
			"setting.hud.reducedmotion.description", Icon.MOVIE, this, false);
	private ComboSetting designSetting = new ComboSetting("setting.design", "setting.design.description", Icon.PALETTE,
			this, Arrays.asList("design.simple", "design.classic", "design.clear", "design.materialyou"),
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
}
