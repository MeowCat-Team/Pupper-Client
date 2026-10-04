package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.server.ServerUtils;

public class ServerIPDisplayMod extends SimpleHUDMod {

	public ServerIPDisplayMod() {
		super("mod.serveripdisplay.name", "mod.serveripdisplay.description", Icon.DNS);
	}

	public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
		this.draw();
	};

	@Override
	public String getText() {

		if (ServerUtils.isSingleplayer()) {
			return "Singleplayer";
		}

		return ServerUtils.getAddress();
	}

	@Override
	public String getIcon() {
		return Icon.DNS;
	}
}
