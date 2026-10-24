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

package cn.pupperclient.mixin.mixins.minecraft.network;

import cn.pupperclient.utils.network.ServerTextContents;
import cn.pupperclient.utils.network.ServerTextPrivacy;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeybindContents.class)
public abstract class MixinKeybindContents implements ServerTextContents {
    @Shadow @Final private String name;
    @Unique private boolean pupper$serverSupplied;

    @Override
    public void pupper$markServerSupplied() {
        pupper$serverSupplied = true;
    }

    @Inject(method = "getNestedComponent", at = @At("HEAD"), cancellable = true)
    private void pupper$hideRemoteModKeybind(CallbackInfoReturnable<Component> cir) {
        if (ServerTextPrivacy.protectKeybind(name, pupper$serverSupplied)) {
            cir.setReturnValue(Component.literal(name));
        }
    }
}
