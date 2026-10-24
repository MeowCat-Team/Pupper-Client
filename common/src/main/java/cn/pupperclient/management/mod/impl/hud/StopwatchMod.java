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

import java.text.DecimalFormat;

import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.management.mod.settings.impl.KeybindSetting;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.time.TimerUtils;
import com.mojang.blaze3d.platform.InputConstants;

public class StopwatchMod extends SimpleHUDMod {

	private TimerUtils timer = new TimerUtils();
	private int pressCount;
	private float currentTime;
	private DecimalFormat timeFormat = new DecimalFormat("0.00");

	private KeybindSetting keybindSetting = new KeybindSetting("setting.keybind", "setting.keybind.description",
			Icon.KEYBOARD, this, InputConstants.getKey(new KeyEvent(GLFW.GLFW_KEY_P, 0, 0)));

	public StopwatchMod() {
		super("mod.stopwatch.name", "mod.stopwatch.description", Icon.TIMER);
	}

	public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
		this.draw();
	};

	public final EventBus.EventListener<ClientTickEvent> onClientTick = event -> {

		if (keybindSetting.isPressed()) {
			pressCount++;
		}

		switch (pressCount) {
		case 0:
			timer.reset();
			break;
		case 1:
			currentTime = (timer.getElapsedTime() / 1000F);
			break;
		case 3:
			timer.reset();
			currentTime = 0;
			pressCount = 0;
			break;
		}
	};

	@Override
	public void onEnable() {

		super.onEnable();

		if (timer != null) {
			timer.reset();
		}

		pressCount = 0;
		currentTime = 0;
	}

	@Override
	public String getText() {
		return timeFormat.format(currentTime) + " s";
	}

	@Override
	public String getIcon() {
		return Icon.TIMER;
	}
    @Override protected String getUnit() { return "s"; }
}
