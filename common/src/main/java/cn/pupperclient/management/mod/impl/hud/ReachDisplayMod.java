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

import java.text.DecimalFormat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.server.impl.AttackEntityEvent;
import cn.pupperclient.event.server.impl.DamageEntityEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.thread.Multithreading;

public class ReachDisplayMod extends SimpleHUDMod {

	private DecimalFormat df = new DecimalFormat("0.##");

	private double distance = 0;
	private long hitTime = -1;
	private int possibleTarget;

	public ReachDisplayMod() {
		super("mod.reachdisplay.name", "mod.reachdisplay.description", Icon.SOCIAL_DISTANCE);
	}


	public final EventBus.EventListener<DamageEntityEvent> onDamageEntity = event -> {
		// Damage packets arrive on the network thread; world and player state is
		// only valid on the client thread.
		Multithreading.runMainThread(() -> measureReach(event.getEntityId()));
	};

	private void measureReach(int entityId) {
		if (entityId != possibleTarget) return;
		possibleTarget = -1;

		if (client.level == null || client.player == null) return;
		Entity entity = client.level.getEntity(entityId);
		if (entity == null) return;

		Vec3 eye = client.player.getEyePosition();
		distance = eye.distanceTo(closestPointToBox(eye, entity.getBoundingBox()));
		hitTime = System.currentTimeMillis();
	}

	public final EventBus.EventListener<AttackEntityEvent> onAttackEntity = event -> {
		possibleTarget = event.getEntityId();
	};

	private Vec3 closestPointToBox(Vec3 start, AABB box) {
		return new Vec3(coerceIn(start.x, box.minX, box.maxX), coerceIn(start.y, box.minY, box.maxY),
				coerceIn(start.z, box.minZ, box.maxZ));
	}

	private double coerceIn(double target, double min, double max) {
		if (target > max) {
			return max;
		}
		return Math.max(target, min);
	}

	@Override
	public String getText() {

		if ((System.currentTimeMillis() - hitTime) > 5000) {
			distance = 0;
		}

		if (distance == 0) {
			return "Hasn't attacked";
		} else {
			return df.format(distance) + " blocks";
		}
	}

	@Override
	public String getIcon() {
		return Icon.SOCIAL_DISTANCE;
	}
}
