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

package cn.pupperclient.mixin.mixins.minecraft.client;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.mod.settings.impl.KeybindSetting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;

@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboard {
    @Inject(
        method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/KeyMapping;click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V",
            shift = At.Shift.AFTER
        )
    )
    private void onKeyPressed(long handle, int action, net.minecraft.client.input.KeyEvent event, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
            InputConstants.Key inputKey = InputConstants.getKey(event);
            for (KeybindSetting setting : PupperClient.getInstance().getModManager().getKeybindSettings()) {
                if (setting.getKey().equals(inputKey)) {
                    setting.setPressed();
                    setting.setKeyDown(true);
                }
            }
        }
    }

    @Inject(
        method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/KeyMapping;set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V",
            shift = At.Shift.AFTER
        )
    )
    private void onKeyReleased(long handle, int action, net.minecraft.client.input.KeyEvent event, CallbackInfo ci) {
        if (action == GLFW.GLFW_RELEASE) {
            InputConstants.Key inputKey = InputConstants.getKey(event);
            for (KeybindSetting setting : PupperClient.getInstance().getModManager().getKeybindSettings()) {
                if (setting.getKey().equals(inputKey)) {
                    setting.setKeyDown(false);
                }
            }
        }
    }

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"))
    private void onKeyPress(long handle, int action, net.minecraft.client.input.KeyEvent event, CallbackInfo ci) {
        int key = event.key();
        if (key != -1 && EventBus.getInstance() != null) {
            EventBus.getInstance().post(new KeyEvent(key, action != 0));
        }
    }
}
