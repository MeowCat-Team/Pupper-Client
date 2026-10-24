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

package cn.pupperclient.utils.file;

import java.io.File;
import net.minecraft.client.Minecraft;

public class FileLocation {

	public static final File MAIN_DIR = new File(Minecraft.getInstance().gameDirectory, "pupper");
	public static final File MUSIC_DIR = new File(MAIN_DIR, "music");
	public static final File CACHE_DIR = new File(MAIN_DIR, "cache");
	public static final File CONFIG_DIR = new File(MAIN_DIR, "config");
	public static final File PROFILE_DIR = new File(MAIN_DIR, "profile");
    public static final File BACKGROUND_DIR = new File(MAIN_DIR, "background");
    public static final File CAPES_DIR = new File(MAIN_DIR, "capes");
    public static final File SKINS_DIR = new File(MAIN_DIR, "skins");

    public static void init() {
		FileUtils.createDir(MAIN_DIR);
		FileUtils.createDir(MUSIC_DIR);
		FileUtils.createDir(CACHE_DIR);
		FileUtils.createDir(CONFIG_DIR);
		FileUtils.createDir(PROFILE_DIR);
        FileUtils.createDir(BACKGROUND_DIR);
        FileUtils.createDir(CAPES_DIR);
        FileUtils.createDir(SKINS_DIR);
	}
}
