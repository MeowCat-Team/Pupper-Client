package cn.pupperclient.event.client;

import cn.pupperclient.event.Event;

public class ServerJoinEvent extends Event {

    private final String address;

    public ServerJoinEvent(final String address) {
        this.address = address;
    }

    public String getAddress() {
        return this.address;
    }
}
