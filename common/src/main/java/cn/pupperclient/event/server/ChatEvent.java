package cn.pupperclient.event.server;

import cn.pupperclient.event.Event;

public class ChatEvent extends Event {
    private final String message;

    public ChatEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
