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

import java.util.Comparator;
import java.util.List;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.AnimatedListHUDMod;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.misc.RomanConverter;
import net.minecraft.world.effect.MobEffectInstance;

public class PotionHudMod extends AnimatedListHUDMod {
    private static PotionHudMod instance;
    public PotionHudMod() {
        super("mod.potionhud.name", "mod.potionhud.description", Icon.SCIENCE);
        instance = this;
    }
    public static PotionHudMod getInstance() { return instance; }
    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> drawList();

    @Override protected List<Row> rows() {
        if (client.player == null) return List.of();
        return client.player.getActiveEffects().stream()
            .sorted(Comparator.comparing(effect -> effect.getEffect().value().getDescriptionId()))
            .map(effect -> {
                var type = effect.getEffect().value();
                int level = effect.getAmplifier() + 1;
                String label = type.getDisplayName().getString()
                    + (level > 1 ? " " + RomanConverter.intToRomanByPlace(level) : "");
                boolean urgent = !effect.isInfiniteDuration() && effect.getDuration() <= 200;
                return new Row(type.getDescriptionId(), label, duration(effect),
                    urgent ? Icon.TIMER : Icon.SCIENCE, urgent);
            }).toList();
    }

    private String duration(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) return "∞";
        int seconds = Math.max(0, effect.getDuration() / 20);
        return seconds >= 60 ? String.format("%d:%02d", seconds / 60, seconds % 60) : seconds + "s";
    }
}