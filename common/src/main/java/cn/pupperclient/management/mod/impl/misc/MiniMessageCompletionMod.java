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

package cn.pupperclient.management.mod.impl.misc;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.skia.font.Icon;

/** Opt-in chat input aid, persisted through the ordinary module configuration. */
public final class MiniMessageCompletionMod extends Mod {
    private static MiniMessageCompletionMod instance;

    public MiniMessageCompletionMod() {
        super("mod.minimessage.name", "mod.minimessage.description", Icon.CODE, ModCategory.MISC);
        instance = this;
    }

    public static boolean isCompletionEnabled() {
        return instance != null && instance.isEnabled();
    }
}
