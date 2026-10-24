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

package cn.pupperclient.management.mod.settings;

import java.util.Set;

/** Presentation only: saved values and module behavior remain independent of the settings page. */
public final class SettingPresentation {
    public enum Level { BASIC, MORE, INTERNAL }

    private static final Set<String> INTERNAL = Set.of(
        "setting.blurtype", "setting.clickeffect.menuonly");
    private static final Set<String> MORE = Set.of(
        "setting.blurintensity", "setting.ui.backgroundopacity", "setting.hud.backgroundopacity",
        "setting.ytdlppath", "setting.ffmpegpath", "setting.ytdlpcommand",
        "setting.icon", "setting.background", "setting.cover.animation.name",
        "setting.zoomspeed", "setting.smoothcamera", "setting.refreshtime", "setting.delay",
        "setting.alwayssharpness", "setting.alwayscriticals", "setting.sharpnessamount", "setting.criticalsamount",
        "setting.clickeffect.color", "setting.clickeffect.radius", "setting.clickeffect.duration",
        "setting.entityoptimizer.threshold", "setting.entityoptimizer.shadows",
        "setting.unmarked", "setting.snaptapcompatibility", "setting.max", "setting.maxtick",
        "setting.hud", "setting.render", "setting.player", "setting.other",
        "setting.text2key", "setting.text2", "setting.text3key", "setting.text3",
        "setting.disableattackcooldown", "setting.oldpvpsounds", "setting.disableheartflash",
        "mod.heypixel.logging.name", "mod.nofov.nohurtFov.name");

    private SettingPresentation() { }

    public static Level level(String key) {
        return INTERNAL.contains(key) ? Level.INTERNAL : MORE.contains(key) ? Level.MORE : Level.BASIC;
    }

    public static boolean shown(Setting setting, boolean more, boolean searching) {
        return setting.isVisible() && switch (level(setting.getName())) {
            case BASIC -> true;
            case MORE -> more || searching;
            case INTERNAL -> false;
        };
    }
}
