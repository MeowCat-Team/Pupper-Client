package cn.pupperclient.management.mod.impl.render;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.server.impl.AttackEntityEvent;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.management.mod.settings.impl.NumberSetting;
import cn.pupperclient.skia.font.Icon;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;

public class BloodParticleMod extends Mod {

	private BooleanSetting soundSetting = new BooleanSetting("setting.sound", "setting.sound.description",
			Icon.EQUALIZER, this, true);
	private NumberSetting multiplierSetting = new NumberSetting("setting.multiplier",
			"setting.bloodparticle.multiplier.description", Icon.CALCULATE, this, 2, 1, 10, 1);

	public BloodParticleMod() {
		super("mod.bloodparticles.name", "mod.bloodparticles.description", Icon.MENSTRUAL_HEALTH, ModCategory.RENDER);
	}

	public final EventBus.EventListener<AttackEntityEvent> onAttackEntity = event -> {

        Entity target = null;
        if (client.level != null) {
            target = client.level.getEntity(event.getEntityId());
        }

        if (target instanceof LivingEntity) {
			for (int i = 0; i < multiplierSetting.getValue(); i++) {
				client.particleEngine.createTrackingEmitter(target,
						new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()));
			}
		}

		if (soundSetting.isEnabled() && target != null) {
			client.getSoundManager()
					.play(new SimpleSoundInstance(SoundEvents.STONE_BREAK.location(), SoundSource.BLOCKS, 4.0F,
							1.2F, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.LINEAR,
							target.getX(), target.getY(), target.getZ(), false));
		}
	};
}
