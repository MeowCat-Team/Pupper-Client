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

package cn.pupperclient.management.mod.impl.player;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.platform.ProtocolAccess;

public class OldAnimationsMod extends Mod {

	private static OldAnimationsMod instance;
	private BooleanSetting oldBreakingSetting = new BooleanSetting("setting.oldbreaking",
			"setting.oldbreaking.description", Icon.SWORDS, this, true);
	private BooleanSetting disableAttackCooldownSetting = new BooleanSetting("setting.disableattackcooldown",
			"setting.disableattackcooldown.description", Icon.SCHEDULE, this, true);
	private BooleanSetting oldPvPSoundsSetting = new BooleanSetting("setting.oldpvpsounds",
			"setting.oldpvpsounds.description", Icon.SPEAKER, this, true);
	private BooleanSetting disableHeartFlashSetting = new BooleanSetting("setting.disableheartflash",
			"setting.disableheartflash.description", Icon.HEART_BROKEN, this, true);
	private BooleanSetting oldBowSetting = new BooleanSetting("setting.oldbow",
			"setting.oldbow.description", Icon.AZM, this, false);
	private BooleanSetting oldRodSetting = new BooleanSetting("setting.oldrod",
			"setting.oldrod.description", Icon.PHISHING, this, false);

	public OldAnimationsMod() {
		super("mod.oldanimations.name", "mod.oldanimations.description", Icon.ANIMATION, ModCategory.PLAYER);

		instance = this;
	}

	public static OldAnimationsMod getInstance() {
		return instance;
	}

	public boolean isOldBreaking() {
		return isv1_8() && oldBreakingSetting.isEnabled();
	}
	
	public boolean isDisableAttackCooldown() {
		return isv1_8() && disableAttackCooldownSetting.isEnabled();
	}
	
	public boolean isOldPvPSounds() {
		return isv1_8() && oldPvPSoundsSetting.isEnabled();
	}
	
	public boolean isDisableHeartFlash() {
		return isv1_8() && disableHeartFlashSetting.isEnabled();
	}
	
	public boolean isOldRod() {
		return isv1_8() && oldRodSetting.isEnabled();
	}
	
	public boolean isOldBow() {
		return isv1_8() && oldBowSetting.isEnabled();
	}
	
	private boolean isv1_8() {
		return ProtocolAccess.isVersion1_8();
	}
}
