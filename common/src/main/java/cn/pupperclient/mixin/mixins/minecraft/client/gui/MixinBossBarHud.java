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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import cn.pupperclient.management.mod.impl.hud.BossBarMod;
import cn.pupperclient.mixin.interfaces.IMixinBossHealthOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public abstract class MixinBossBarHud implements IMixinBossHealthOverlay {
    @Shadow @Final private Map<UUID, LerpingBossEvent> events;

    @Override public Collection<LerpingBossEvent> pupper$getBossEvents() {
        return List.copyOf(events.values());
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void onExtractRenderState(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        BossBarMod mod = BossBarMod.getInstance();
        if (mod != null && (!mod.isEnabled() || !mod.isVanillaPosition())) ci.cancel();
    }
}