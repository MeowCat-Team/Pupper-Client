package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.skia.font.Icon;

public class CoordsMod extends SimpleHUDMod {

	public CoordsMod() {
		super("mod.coords.name", "mod.coords.description", Icon.PIN_DROP);
	}

	public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
		this.draw();
	};

	@Override
	public String getText() {
		if (client.player != null) {
			return "X: " + (int) client.player.getX() + " Y: " + (int) client.player.getY() + " Z: "
					+ (int) client.player.getZ();
		}
        return "";
    }

	@Override
	public String getIcon() {
		return Icon.PIN_DROP;
	}
}
