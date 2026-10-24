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

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import cn.pupperclient.mixin.interfaces.IMixinMinecraftClient;

public class SkinUtils {

	public static File getSkin(Identifier identifier) {
		String fileName = identifier.getPath().replace("skins/", "");
		String folder = fileName.substring(0, 2);
		File file = new File(((IMixinMinecraftClient) Minecraft.getInstance()).pupper$getAssetDir(),
				"skins/" + folder + "/" + fileName);
		return file;
	}
}
