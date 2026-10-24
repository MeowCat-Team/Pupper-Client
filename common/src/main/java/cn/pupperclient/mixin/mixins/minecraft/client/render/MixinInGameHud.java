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

package cn.pupperclient.mixin.mixins.minecraft.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.RenderGameOverlayEvent;
import cn.pupperclient.management.mod.impl.hud.ItemInfoMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;

@Mixin(Hud.class)
public class MixinInGameHud {
    // NeoForge also has a height-aware overload; omitting target arguments covers both.
    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true, require = 1)
    private void pupper$itemInfoName(CallbackInfo ci) {
        var itemInfo = ItemInfoMod.getInstance();
        if (itemInfo != null && itemInfo.isEnabled()) ci.cancel();
    }
	// Both loaders extract effects immediately after the hotbar and its decorations.
	// NeoForge splits the decorations into layers and removes the vanilla dispatcher.
	@Inject(method = "extractEffects", at = @At("HEAD"), require = 1)
	private void renderMainHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
		EventBus.getInstance().post(new RenderGameOverlayEvent(context));
	}
}
