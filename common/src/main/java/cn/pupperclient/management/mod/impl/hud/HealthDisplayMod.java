package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.skia.font.Icon;

public class HealthDisplayMod extends SimpleHUDMod {

	public HealthDisplayMod() {
		super("mod.healthdisplay.name", "mod.healthdisplay.description", Icon.FAVORITE);
	}

	public EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
		this.draw();
	};

	@Override
	public String getText() {
        if (client.player != null) {
            return (int) client.player.getHealth() + " Health";
        }
        return "";
    }

	@Override
	public String getIcon() {
		return Icon.FAVORITE;
	}
    @Override protected String getUnit() { return "Health"; }
    @Override protected boolean isUrgent() { return client.player != null && client.player.getHealth() <= client.player.getMaxHealth() * 0.25f; }
}
