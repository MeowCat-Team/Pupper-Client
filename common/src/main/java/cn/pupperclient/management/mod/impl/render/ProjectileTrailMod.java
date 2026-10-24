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

package cn.pupperclient.management.mod.impl.render;

import java.util.Arrays;
import java.util.Objects;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.ComboSetting;
import cn.pupperclient.skia.font.Icon;

public class ProjectileTrailMod extends Mod {

	private ComboSetting effectSetting = new ComboSetting("setting.effect", "setting.effect.description",
			Icon.EXPLOSION, this,
			Arrays.asList("setting.blacksmoke", "setting.fire", "setting.greenstar", "setting.hearts", "setting.magic",
					"setting.musicnote", "setting.slime", "setting.spark", "setting.swirl", "setting.whitesmoke"),
			"setting.hearts");

	public ProjectileTrailMod() {
		super("mod.projectiletrail.name", "mod.projectiletrail.description", Icon.TRAIL_LENGTH, ModCategory.RENDER);
	}

	public final EventBus.EventListener<ClientTickEvent> onClientTick = event -> {

		if (client.player != null && client.level != null) {

			AABB box = new AABB(client.player.position().add(-150.0, -150.0, -150.0),
					client.player.position().add(150.0, 150.0, 150.0));

			for (Projectile projectile : client.level.getEntitiesOfClass(Projectile.class, box,
					entity -> true)) {

				if (!(projectile.getDeltaMovement().lengthSqr() > 0.01) || projectile.onGround()
						|| client.level.getBlockState(projectile.blockPosition()).isRedstoneConductor(client.level,
								projectile.blockPosition()) && Objects.requireNonNull(projectile.getOwner()).getId() != client.player.getId()) {
					continue;
				}

				ParticleType<?> type = getCurrentType();

				if (type instanceof ParticleOptions) {
					client.level.addParticle((ParticleOptions) type, projectile.getX(), projectile.getY(),
							projectile.getZ(), 0.0, 0.0, 0.0);
				}
			}
		}
	};

	private ParticleType<?> getCurrentType() {

		String option = effectSetting.getOption();

		if (option.contains("blacksmoke")) {
			return ParticleTypes.SMOKE;
		} else if (option.contains("fire")) {
			return ParticleTypes.FLAME;
		} else if (option.contains("greenstar")) {
			return ParticleTypes.HAPPY_VILLAGER;
		} else if (option.contains("hearts")) {
			return ParticleTypes.HEART;
		} else if (option.contains("magic")) {
			return ParticleTypes.WITCH;
		} else if (option.contains("musicnote")) {
			return ParticleTypes.NOTE;
		} else if (option.contains("slime")) {
			return ParticleTypes.ITEM_SLIME;
		} else if (option.contains("spark")) {
			return ParticleTypes.FIREWORK;
		} else if (option.contains("whitesmoke")) {
			return ParticleTypes.WHITE_SMOKE;
		}

		return null;
	}
}
