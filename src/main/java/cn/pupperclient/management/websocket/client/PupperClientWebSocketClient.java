package cn.pupperclient.management.websocket.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import cn.pupperclient.PupperLogger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import cn.pupperclient.management.websocket.handler.WebSocketHandler;
import cn.pupperclient.management.websocket.handler.impl.HypixelStatsHandler;
import cn.pupperclient.utils.misc.JsonUtils;

public class PupperClientWebSocketClient extends WebSocketClient {

	private final Map<String, WebSocketHandler> handlers = new HashMap<>();
	private final Gson gson = new Gson();
	private final Runnable closeTask;

	public PupperClientWebSocketClient(Map<String, String> headers, Runnable closeTask) throws URISyntaxException {
		super(new URI("ws://localhost:8080/websocket"), headers);
		this.closeTask = closeTask;
		initializeHandlers();
	}

    private void initializeHandlers() {
    	register("sc-hypixel-stats", new HypixelStatsHandler());
    }
    
	@Override
	public void onOpen(ServerHandshake handshakedata) {
		PupperLogger.info("API", "WebSocket connection opened");
	}

	@Override
	public void onMessage(String message) {

		JsonObject jsonObject = gson.fromJson(message, JsonObject.class);
		
		String type = JsonUtils.getStringProperty(jsonObject, "type", "");

		WebSocketHandler handler = handlers.get(type);

		if (handler != null) {
			handler.handle(jsonObject);
		} else {
			//PupperLogger.warn("API", "No handler found for message type: " + type);
		}
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		//PupperLogger.info("API", "WebSocket connection closed: " + reason);
		closeTask.run();
	}

	@Override
	public void onError(Exception ex) {
		//PupperLogger.error("API", "WebSocket error occurred", ex);
	}

	private void register(String type, WebSocketHandler handler) {
		handlers.put(type, handler);
	}
}
