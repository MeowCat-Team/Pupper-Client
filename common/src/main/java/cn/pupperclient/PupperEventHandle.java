package cn.pupperclient;

import java.util.List;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.*;
import cn.pupperclient.event.server.impl.GameJoinEvent;
import cn.pupperclient.management.profile.Profile;

public class PupperEventHandle {
	public final EventBus.EventListener<ClientTickEvent> onClientTick = event -> {
		PupperClient.getInstance().getColorManager().onTick();
		PupperClient.getInstance().getHypixelManager().update();
	};

	public final EventBus.EventListener<GameJoinEvent> onGameJoin = event -> {
		PupperClient.getInstance().getHypixelManager().clear();
	};

	public final EventBus.EventListener<ServerJoinEvent> onServerJoin = event -> {

		List<Profile> profiles = PupperClient.getInstance().getProfileManager().getProfiles();

		for (Profile p : profiles) {

			String address = p.getServerIp();

			if (event.getAddress().contains(address)) {
				PupperClient.getInstance().getProfileManager().load(p);
				break;
			}
		}
	};
}
