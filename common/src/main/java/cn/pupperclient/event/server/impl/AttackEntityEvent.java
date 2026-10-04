package cn.pupperclient.event.server.impl;

import cn.pupperclient.event.Event;

public class AttackEntityEvent extends Event {

	private final int entityId;

	public AttackEntityEvent(int entityId) {
		this.entityId = entityId;
	}

	public int getEntityId() {
		return entityId;
	}
}
