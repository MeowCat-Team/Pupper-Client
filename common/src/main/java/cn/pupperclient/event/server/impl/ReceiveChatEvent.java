package cn.pupperclient.event.server.impl;

import cn.pupperclient.event.Event;

public class ReceiveChatEvent extends Event {

	private String message;

	public ReceiveChatEvent(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
