/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
import cn.pupperclient.management.mod.setting.BooleanSetting;
import cn.pupperclient.management.mod.setting.NumberSetting;
import cn.pupperclient.management.mod.setting.StringSetting;
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
        if (message == null) return;

        // Chat arrives on the network thread, but the current server and the
        // setting are client state.
        Multithreading.runMainThread(() -> {
            if (!ServerUtils.isMultiplayer()) return;
            if (!ServerUtils.isJoin(Server.HYPIXEL) || !hypixelSetting.isEnabled()) return;
            processChat(strings.get(Server.HYPIXEL), message);
        });
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
        String message = messageSetting.getValue();
        long delay = (long) delaySetting.getValue();
        // The delayed task runs on the scheduler thread; the connection is client state.
        Multithreading.schedule(() -> Multithreading.runMainThread(() -> {
            if (client.player == null) return;
            if (hypixel) {
                client.player.connection.sendCommand("achat " + message);
            } else {
                client.player.connection.sendChat(message);
            }
        }), delay, TimeUnit.SECONDS);
    }

    private List<String> addToList(String... strings) {
        return Arrays.stream(strings).toList();
    }
}
