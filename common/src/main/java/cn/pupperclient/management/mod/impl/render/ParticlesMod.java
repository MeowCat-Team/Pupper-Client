package cn.pupperclient.management.mod.impl.render;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.server.impl.AttackEntityEvent;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.management.mod.settings.impl.NumberSetting;
import cn.pupperclient.skia.font.Icon;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Objects;

public class ParticlesMod extends Mod {

	private BooleanSetting alwaysSharpnessSetting = new BooleanSetting("setting.alwayssharpness",
			"setting.alwayssharpness.description", Icon.CONTRAST, this, false);
	private BooleanSetting alwaysCriticalsSetting = new BooleanSetting("setting.alwayscriticals",
			"setting.alwayscriticals.description", Icon.CONTRAST, this, false);
	private BooleanSetting sharpnessSetting = new BooleanSetting("setting.sharpness", "setting.sharpness.description",
			Icon.FLARE, this, true);
	private BooleanSetting criticalsSetting = new BooleanSetting("setting.criticals", "setting.criticals.description",
			Icon.FLARE, this, true);
	private NumberSetting sharpnessAmountSetting = new NumberSetting("setting.sharpnessamount",
			"setting.sharpnessamount.description", Icon.FILTER_5, this, 2, 1, 10, 1);
	private NumberSetting criticalsAmountSetting = new NumberSetting("setting.criticalsamount",
			"setting.criticalsamount.description", Icon.FILTER_5, this, 2, 1, 10, 1);

	public ParticlesMod() {
		super("mod.particles.name", "mod.particles.description", Icon.FLARE, ModCategory.RENDER);
	}

	public final EventBus.EventListener<AttackEntityEvent> onAttackEntity = event -> {

		int sharpnessAmount = (int) sharpnessAmountSetting.getValue();
		int criticalsAmount = (int) criticalsAmountSetting.getValue();
		Entity target = client.level.getEntity(event.getEntityId());
		LocalPlayer player = client.player;

		boolean critical = criticalsSetting.isEnabled() && player.getAttackStrengthScale(0.5F) > 0.9F
				&& !player.onGround() && !player.onClimbable() && !player.isInWater()
				&& !player.hasEffect(MobEffects.BLINDNESS) && !player.isPassenger()
				&& target instanceof LivingEntity && !player.isSprinting();
		boolean alwaysSharpness = alwaysSharpnessSetting.isEnabled();
		boolean sharpness = sharpnessSetting.isEnabled() && EnchantmentHelper.getItemEnchantmentLevel(client.level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), Objects.requireNonNull(player).getWeaponItem()) > 0;
		boolean alwaysCriticals = alwaysCriticalsSetting.isEnabled();

		if (critical || alwaysCriticals) {
			for (int i = 0; i < criticalsAmount - 1; i++) {
                assert target != null;
                client.particleEngine.createTrackingEmitter(target, ParticleTypes.CRIT);
			}
		}

		if (alwaysSharpness || sharpness) {
			for (int i = 0; i < sharpnessAmount - 1; i++) {
                assert target != null;
                client.particleEngine.createTrackingEmitter(target, ParticleTypes.ENCHANTED_HIT);
			}
		}
	};
}
