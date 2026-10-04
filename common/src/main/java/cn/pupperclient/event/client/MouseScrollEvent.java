package cn.pupperclient.event.client;

import cn.pupperclient.event.Event;

public class MouseScrollEvent extends Event {

	private final double amount;

	public MouseScrollEvent(double amount) {
		this.amount = amount;
	}

	public double getAmount() {
		return amount;
	}
}
