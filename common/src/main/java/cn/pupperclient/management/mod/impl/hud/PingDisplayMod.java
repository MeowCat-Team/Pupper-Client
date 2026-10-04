package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.management.mod.settings.impl.NumberSetting;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.thread.Multithreading;
import cn.pupperclient.utils.time.TimerUtils;
import cn.pupperclient.utils.server.ServerUtils;

import net.lenni0451.mcping.MCPing;

public class PingDisplayMod extends SimpleHUDMod {

	private TimerUtils timer = new TimerUtils();
	private NumberSetting refreshTimeSetting = new NumberSetting("setting.refreshtime",
			"setting.refreshtime.description", Icon.REFRESH, this, 4, 1, 20, 1);
	private long ping;
	private boolean pinging;

	public PingDisplayMod() {
		super("mod.pingdisplay.name", "mod.pingdisplay.description", Icon.WIFI);
		pinging = false;
	}

	public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
		this.draw();
	};
	public final EventBus.EventListener<ClientTickEvent> onClientTick = event -> updatePing();

	private void updatePing() {

		if (timer.delay((long) (1000 * refreshTimeSetting.getValue()))) {

			if (ServerUtils.isMultiplayer() && client.getCurrentServer() != null) {
				var server = client.getCurrentServer();
				if (server.ping <= 1 && !pinging) {
					String address = server.ip;
					pinging = true;
					Multithreading.runAsync(() -> {
						try {
							ping = MCPing.pingModern().address(address).getSync().getPing();
						} finally {
							pinging = false;
						}
					});
				} else {
					ping = server.ping;
				}
			} else if (client.hasSingleplayerServer()) {
				ping = 0;
			}

			timer.reset();
		}
	}

	@Override
	public String getText() {
		return ping + " ms";
	}

	@Override
	public String getIcon() {
		return Icon.WIFI;
	}
    @Override protected String getUnit() { return "ms"; }
}
