package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.platform.ProtocolAccess;

public class ProtocolVersionMod extends SimpleHUDMod {

	public ProtocolVersionMod() {
		super("mod.protocolversion.name", "mod.protocolversion.description", Icon.BRING_YOUR_OWN_IP);
	}

	public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
		this.draw();
	};

	@Override
	public String getText() {
		return "Protocol Version: " + ProtocolAccess.target().name();
	}

	@Override
	public String getIcon() {
		return Icon.BRING_YOUR_OWN_IP;
	}
}
