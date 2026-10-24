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

package cn.pupperclient.management.mod.impl.misc;

import java.time.OffsetDateTime;

import com.google.gson.JsonObject;
import cn.pupperclient.PupperClient;
import cn.pupperclient.libraries.discordipc.IPCClient;
import cn.pupperclient.libraries.discordipc.IPCListener;
import cn.pupperclient.libraries.discordipc.entities.ActivityType;
import cn.pupperclient.libraries.discordipc.entities.Packet;
import cn.pupperclient.libraries.discordipc.entities.RichPresence;
import cn.pupperclient.libraries.discordipc.entities.User;
import cn.pupperclient.libraries.discordipc.entities.pipe.PipeStatus;
import cn.pupperclient.libraries.discordipc.exceptions.NoDiscordClientException;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.skia.font.Icon;

public class DiscordRPCMod extends Mod {

	private IPCClient client;

	public DiscordRPCMod() {
		super("mod.discordrpc.name", "mod.discordrpc.description", Icon.VERIFIED, ModCategory.MISC);
	}

	@Override
	public void onEnable() {
		super.onEnable();

		client = new IPCClient(1059341815205068901L);
		client.setListener(new IPCListener() {
			@Override
			public void onReady(IPCClient client) {

				RichPresence.Builder builder = new RichPresence.Builder();

				builder.setState("Playing Pupper Client " + PupperClient.getInstance().getClientVersion())
						.setStartTimestamp(OffsetDateTime.now().toEpochSecond()).setLargeImage("icon")
						.setActivityType(ActivityType.Playing);

				client.sendRichPresence(builder.build());
			}

			@Override
			public void onPacketSent(IPCClient client, Packet packet) {
			}

			@Override
			public void onPacketReceived(IPCClient client, Packet packet) {
			}

			@Override
			public void onActivityJoin(IPCClient client, String secret) {
			}

			@Override
			public void onActivitySpectate(IPCClient client, String secret) {
			}

			@Override
			public void onActivityJoinRequest(IPCClient client, String secret, User user) {
			}

			@Override
			public void onClose(IPCClient client, JsonObject json) {
			}

			@Override
			public void onDisconnect(IPCClient client, Throwable t) {
			}
		});

		try {
			client.connect();
		} catch (NoDiscordClientException e) {
			cn.pupperclient.PupperLogger.error("DiscordRPC", "Failed to connect to Discord IPC", e);
		}
	}

	@Override
	public void onDisable() {
		super.onDisable();

		if (client != null && client.getStatus() == PipeStatus.CONNECTED) {
			client.close();
			client = null;
		}
	}
}
