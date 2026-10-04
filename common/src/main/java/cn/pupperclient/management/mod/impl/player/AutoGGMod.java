package cn.pupperclient.management.mod.impl.player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.server.impl.ReceiveChatEvent;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.management.mod.settings.impl.NumberSetting;
import cn.pupperclient.management.mod.settings.impl.StringSetting;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.thread.Multithreading;
import cn.pupperclient.utils.server.Server;
import cn.pupperclient.utils.server.ServerUtils;

public class AutoGGMod extends Mod {

	private final Map<Server, List<String>> strings = new HashMap<>();

	private NumberSetting delaySetting = new NumberSetting("setting.delay", "setting.delay.description", Icon.TIMER,
			this, 0, 0, 5, 1);
	private StringSetting messageSetting = new StringSetting("setting.message", "setting.message.description",
			Icon.TEXT_FIELDS, this, "gg");
	private BooleanSetting hypixelSetting = new BooleanSetting("setting.hypixel", "setting.hypixel.description",
			Icon.TOGGLE_OFF, this, true);

	public AutoGGMod() {
		super("mod.autogg.name", "mod.autogg.description", Icon.TROPHY, ModCategory.PLAYER);

		strings.put(Server.HYPIXEL,
				addToList("1st Killer -", "1st Place -", "Winner:", " - Damage Dealt -", "Winning Team -", "1st -",
						"Winners:", "Winner:", "Winning Team:", " won the game!", "Top Seeker:", "1st Place:",
						"Last team standing!", "Winner #1 (", "Top Survivors", "Winners -", "Sumo Duel -",
						"Most Wool Placed -", "Your Overall Winstreak:"));
	}

	public final EventBus.EventListener<ReceiveChatEvent> onReceiveChat = event -> {

		String message = event.getMessage();

		if (message != null) {
			if (ServerUtils.isMultiplayer()) {
				if (ServerUtils.isJoin(Server.HYPIXEL) && hypixelSetting.isEnabled()) {
					processChat(strings.get(Server.HYPIXEL), message);
				}
			}
		}
	};

	private void processChat(List<String> options, String message) {
		for (String s : options) {
			if (message.contains(s)) {
				sendMessage(true);
				break;
			}
		}
	}

	private void sendMessage(boolean hypixel) {
		Multithreading.schedule(() -> {
			if (hypixel) {
                if (client.player != null) {
                    client.player.connection.sendCommand("achat " + messageSetting.getValue());
                }
            } else {
                if (client.player != null) {
                    client.player.connection.sendChat(messageSetting.getValue());
                }
            }
		}, (long) delaySetting.getValue(), TimeUnit.SECONDS);
	}

	private List<String> addToList(String... strings) {
		return Arrays.stream(strings).toList();
	}
}
