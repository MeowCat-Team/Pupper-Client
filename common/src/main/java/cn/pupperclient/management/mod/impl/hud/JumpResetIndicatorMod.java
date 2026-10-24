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
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.management.mod.settings.impl.NumberSetting;
import cn.pupperclient.skia.font.Icon;

public class JumpResetIndicatorMod extends SimpleHUDMod {

	private static JumpResetIndicatorMod instance;

    private NumberSetting tickSetting = new NumberSetting("setting.maxtick", "setting.maxtick.description",
			Icon.SCHEDULE, this, 10, 1, 100, 1);

    private int hurtAge, jumpAge;

    private long lastTime;

	public JumpResetIndicatorMod() {
		super("mod.jumpresetindicator.name", "mod.jumpresetindicator.description", Icon.SPORTS_KABADDI);
		instance = this;
	}

	public EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
		this.draw();
	};

	@Override
	public String getText() {

		String diff = "No Jump";

		if (lastTime + 2500 <= System.currentTimeMillis() || Math.abs(jumpAge - hurtAge) >= tickSetting.getValue()) {
			diff = "No Jump";
		} else if (jumpAge == hurtAge + 1) {
			diff = "Perfect!";
		} else if (hurtAge + 1 < jumpAge) {
			diff = "Late: ".concat(String.valueOf(jumpAge - hurtAge + 1)).concat(" Tick");
		} else if (hurtAge + 1 > jumpAge) {
			diff = "Early: ".concat(String.valueOf(hurtAge + 1 - jumpAge)).concat(" Tick");
		}

		return diff;
	}

	@Override
	public String getIcon() {
		return Icon.SPORTS_KABADDI;
	}

	public static JumpResetIndicatorMod getInstance() {
		return instance;
	}

	public void setHurtAge(int hurtAge) {
		this.hurtAge = hurtAge;
	}

	public void setJumpAge(int jumpAge) {
		this.jumpAge = jumpAge;
	}

	public void setLastTime(long lastTime) {
		this.lastTime = lastTime;
	}
}
