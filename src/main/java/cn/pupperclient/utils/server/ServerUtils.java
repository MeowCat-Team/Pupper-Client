package cn.pupperclient.utils.server;

import java.util.Objects;
import net.minecraft.client.Minecraft;

public class ServerUtils {

	private static final Minecraft client = Minecraft.getInstance();

	public static boolean isJoin(Server server) {
		return isMultiplayer() && getAddress().contains(server.getAddress());
	}

	public static boolean isSingleplayer() {
		return client.hasSingleplayerServer();
	}

	public static boolean isMultiplayer() {
		return client.getCurrentServer() != null;
	}

	public static String getAddress() {
		return isMultiplayer() ? Objects.requireNonNull(client.getCurrentServer()).ip : "null";
	}
}
