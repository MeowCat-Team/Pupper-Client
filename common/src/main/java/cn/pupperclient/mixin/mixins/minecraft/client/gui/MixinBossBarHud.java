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