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

package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.event.server.impl.AttackEntityEvent;
import cn.pupperclient.event.server.impl.DamageEntityEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.skia.font.Icon;

public class ComboCounterMod extends SimpleHUDMod {

	private long hitTime = -1;
	private int combo, possibleTarget;

	public ComboCounterMod() {
		super("mod.combocounter.name", "mod.combocounter.description", Icon.PERSON_ADD);
	}

	public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
		this.draw();
	};

	public final EventBus.EventListener<AttackEntityEvent> onAttackEntity = event -> {
		possibleTarget = event.getEntityId();
	};

	public final EventBus.EventListener<DamageEntityEvent> onDamageEntity = event -> {

		if (event.getEntityId() == possibleTarget) {
			combo++;
			possibleTarget = -1;
			hitTime = System.currentTimeMillis();
		} else if (event.getEntityId() == client.player.getId()) {
			combo = 0;
		}
	};

	public final EventBus.EventListener<ClientTickEvent> onClientTick = event -> {
		if ((System.currentTimeMillis() - hitTime) > 2000) {
			combo = 0;
		}
	};

	@Override
	public String getText() {
		if (combo == 0) {
			return "No Combo";
		} else {
			return combo + " Combo";
		}
	}

	@Override
	public String getIcon() {
		return Icon.PERSON_ADD;
	}
}
