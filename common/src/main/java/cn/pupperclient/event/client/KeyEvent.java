package cn.pupperclient.event.client;

import cn.pupperclient.event.Event;

public class KeyEvent extends Event {

    private final int key;
    private final boolean state;

	public KeyEvent(int key, boolean state) {
        this.key = key;
        this.state = state;
	}

    public int getKeybind() {
        return this.key;
    }

    public boolean isState() {
        return this.state;
    }
}
