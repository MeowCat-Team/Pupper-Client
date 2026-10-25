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

package cn.pupperclient.mixin.core.minecraft.client;

import cn.pupperclient.PupperClient;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientBrandRetriever.class)
public class MixinClientBrandRetriever {
    /**
     * Replaces the returned brand instead of the whole method, so the vanilla body still
     * runs if Minecraft changes it.
     */
    @Inject(method = "getClientModName", at = @At("RETURN"), cancellable = true)
    private static void onGetClientModName(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(PupperClient.getInstance().getName() + " (" + PupperClient.getInstance().getVersion() + ") ");
    }
}
