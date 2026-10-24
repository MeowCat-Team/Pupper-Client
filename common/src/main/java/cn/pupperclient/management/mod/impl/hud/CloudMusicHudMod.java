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
import cn.pupperclient.utils.system.JNAWindowChecker;

public class CloudMusicHudMod extends SimpleHUDMod {
    public CloudMusicHudMod() {
        super("mod.cloudmusic.name", "mod.cloudmusic.description", Icon.MUSIC_VIDEO);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        JNAWindowChecker.startBackgroundMonitoring();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        JNAWindowChecker.stopBackgroundMonitoring();
    }

    @EventListener
    public void onSkiaRender(RenderSkiaEvent event) {
        this.draw();
    }

    @Override
    public String getText() {
		String title = JNAWindowChecker.getCurrentWindowTitle();
		return title == null || title.isBlank() ? "Nothing is playing" : title;
    }

    @Override
    public String getIcon() {
        return Icon.MUSIC_NOTE;
    }

}
