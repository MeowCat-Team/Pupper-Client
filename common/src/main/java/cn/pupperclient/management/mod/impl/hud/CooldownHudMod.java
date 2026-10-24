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

import cn.pupperclient.event.EventListener;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.skia.font.Icon;

public class CooldownHudMod extends SimpleHUDMod {
    public CooldownHudMod() {
        super("mod.cooldown.name", "mod.cooldown.description", "");
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @EventListener
    public void onSkiaRender(RenderSkiaEvent event) {
        this.draw();
    }

    @Override
    public String getText() {
        float cooldownProgress = 0;
        if (client.player != null) {
            cooldownProgress = client.player.getAttackStrengthScale(0.0F);
        }
        boolean isCooldown = cooldownProgress < 1.0f;
		return isCooldown ? "Cooldown " + Math.round(cooldownProgress * 100) + "%" : "Ready";
    }

    @Override
    public String getIcon() {
		return Icon.TIMER;
    }
}
