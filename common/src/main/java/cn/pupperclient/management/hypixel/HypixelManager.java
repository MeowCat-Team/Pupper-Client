package cn.pupperclient.management.hypixel;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import cn.pupperclient.PupperClient;
import cn.pupperclient.management.hypixel.api.HypixelUser;
import cn.pupperclient.management.websocket.packet.impl.SC_HypixelStatsPacket;
import cn.pupperclient.utils.time.TimerUtils;
import cn.pupperclient.utils.server.Server;
import cn.pupperclient.utils.server.ServerUtils;

public class HypixelManager {

	private final Cache<String, HypixelUser> cache = Caffeine.newBuilder().maximumSize(1000).build();
	private final Set<String> requests = new HashSet<>();
	private final TimerUtils timer = new TimerUtils();

	public HypixelManager() {
	}

	public void add(HypixelUser user) {
		cache.put(user.getUuid(), user);
	}

	public void update() {

		Iterator<String> iterator = requests.iterator();

		if (ServerUtils.isJoin(Server.HYPIXEL)) {
			if (timer.delay(500)) {

				if (iterator.hasNext()) {
					String request = iterator.next();
					PupperClient.getInstance().getWebSocketManager().send(new SC_HypixelStatsPacket(request));
					requests.remove(request);
				}

				timer.reset();
			}
		} else {
			timer.reset();
		}
	}

	public HypixelUser getByUuid(String uuid) {

		HypixelUser user = cache.getIfPresent(uuid);

		if (user == null) {
			user = new HypixelUser(uuid, "-1", "-1", "-1", "-1", "-1");
			cache.put(uuid, user);
			requests.add(uuid);
		}

		return user;
	}

	public void clear() {
		requests.clear();
	}
}
