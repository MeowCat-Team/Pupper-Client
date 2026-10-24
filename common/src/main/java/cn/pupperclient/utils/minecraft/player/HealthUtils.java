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

package cn.pupperclient.utils.minecraft.player;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

public class HealthUtils {
    private static final String[] HP_KEYWORDS = {"hp", "health", "♥", "lives"};

    public static float getActualHealth(LivingEntity entity, boolean fromScoreboard) {
        if (fromScoreboard) {
            Float health = getHealthFromScoreboard(entity);
            if (health != null) {
                return health;
            }
        }
        return entity.getHealth();
    }

    public static float getActualHealth(LivingEntity entity) {
        return getActualHealth(entity, true);
    }

    private static Float getHealthFromScoreboard(LivingEntity entity) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME);

        if (objective == null) return null;

        try {
            var score = objective.getScoreboard().getPlayerScoreInfo(entity, objective);
            if (score == null) return null;

            var displayName = objective.getDisplayName();

            if (score.value() <= 0 || displayName == null || !containsHealthKeyword(displayName.getString())) {
                return null;
            }

            return (float) score.value();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean containsHealthKeyword(String text) {
        String lowerText = text.toLowerCase();
        for (String keyword : HP_KEYWORDS) {
            if (lowerText.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
