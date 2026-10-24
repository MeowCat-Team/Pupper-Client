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

package cn.pupperclient.mixin.mixins.minecraft.client.gui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ServerJoinEvent;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;

@Mixin(JoinMultiplayerScreen.class)
public class MixinMultiplayerScreen {

    @Inject(method = "join(Lnet/minecraft/client/multiplayer/ServerData;)V", at = @At("HEAD"))
    private void onConnect(ServerData server, CallbackInfo ci) {
        EventBus.getInstance().post(new ServerJoinEvent(server.ip));
    }

    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    public void onRemoved(CallbackInfo ci) {
        if (((JoinMultiplayerScreen) (Object) this).serverSelectionList == null) {
            ci.cancel();
        }
    }
}
