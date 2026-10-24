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

package cn.pupperclient.management.mod.impl.settings;

import java.io.File;
import java.util.Arrays;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.ComboSetting;
import cn.pupperclient.management.mod.settings.impl.FileSetting;
import cn.pupperclient.management.mod.settings.impl.StringSetting;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.system.OS;

public class SystemSettings extends Mod {

	private static SystemSettings instance;
	private ComboSetting blurTypeSetting = new ComboSetting("setting.blurtype", "setting.blurtype.description",
			Icon.BLUR_MEDIUM, this, Arrays.asList("setting.fastblur", "setting.normalblur"), "setting.normalblur");

	private FileSetting ytdlpPathSetting;
	private FileSetting ffmpegPathSetting;
	private StringSetting ytdlpCommandSetting;

	public SystemSettings() {
		super("mod.systemsettings.name", "mod.systemsettings.description", Icon.SETTINGS, ModCategory.MISC);
		instance = this;
		this.setHidden(true);
		this.setEnabled(true);

		if (OS.isWindows()) {
			ytdlpPathSetting = new FileSetting("setting.ytdlppath", "setting.ytdlppath.description",
					Icon.CONVERSION_PATH, this, null, "exe");
			ffmpegPathSetting = new FileSetting("setting.ffmpegpath", "setting.ffmpegpath.description",
					Icon.CONVERSION_PATH, this, null, "exe");
		} else if (OS.isMacOS() || OS.isLinux()) {
			ytdlpCommandSetting = new StringSetting("setting.ytdlpcommand", "setting.ytdlpcommand.description",
					Icon.TERMINAL, this, "yt-dlp");
		}
	}

	@Override
	public void onDisable() {
		this.setEnabled(true);
	}

	public static SystemSettings getInstance() {
		return instance;
	}

	public boolean isFastBlur() {
		return blurTypeSetting.getOption().contains("fastblur");
	}

	public File getYtdlpPath() {
		return ytdlpPathSetting != null && ytdlpPathSetting.getFile() != null ? ytdlpPathSetting.getFile() : null;
	}

	public File getFFmpegPath() {
		return ffmpegPathSetting != null && ffmpegPathSetting.getFile() != null ? ffmpegPathSetting.getFile() : null;
	}

    public FileSetting getFfmpegPathSetting() {
        return ffmpegPathSetting;
    }

    public FileSetting getYtdlpPathSetting() {
        return ytdlpPathSetting;
    }

    public String getYtdlpCommand() {
		return ytdlpCommandSetting != null ? ytdlpCommandSetting.getValue() : "";
	}
}
