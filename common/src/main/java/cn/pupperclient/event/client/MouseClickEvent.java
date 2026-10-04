package cn.pupperclient.event.client;

import cn.pupperclient.event.Event;

public class MouseClickEvent extends Event {
    private final int button;
    private final double mouseX;
    private final double mouseY;

    public MouseClickEvent(int button, double mouseX, double mouseY) {
        this.button = button;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    public int getButton() {
        return button;
    }

    public double getMouseX() {
        return mouseX;
    }

    public double getMouseY() {
        return mouseY;
    }
}
