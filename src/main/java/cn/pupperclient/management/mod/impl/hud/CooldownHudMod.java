package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.event.EventListener;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.skia.font.Icon;

public class CooldownHudMod extends SimpleHUDMod {
    public CooldownHudMod() {
        super("mod.cooldown.name", "mod.cooldown.description", "");
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @EventListener
    public void onSkiaRender(RenderSkiaEvent event) {
        this.draw();
    }

    @Override
    public String getText() {
        float cooldownProgress = 0;
        if (client.player != null) {
            cooldownProgress = client.player.getAttackStrengthScale(0.0F);
        }
        boolean isCooldown = cooldownProgress < 1.0f;
		return isCooldown ? "Cooldown " + Math.round(cooldownProgress * 100) + "%" : "Ready";
    }

    @Override
    public String getIcon() {
		return Icon.TIMER;
    }
}
