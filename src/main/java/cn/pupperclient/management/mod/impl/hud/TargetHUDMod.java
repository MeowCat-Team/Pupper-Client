package cn.pupperclient.management.mod.impl.hud;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.gui.edithud.api.HUDCore;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.management.mod.api.hud.design.HUDMotion;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.management.mod.settings.impl.ComboSetting;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.minecraft.player.HealthUtils;
import cn.pupperclient.utils.minecraft.player.SkinUtils;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

public class TargetHUDMod extends HUDMod {
    private static TargetHUDMod instance;
    private static final float WIDTH = 180, HEIGHT = 56, AVATAR = 32;
    private Player targetPlayer;
    private long lastAttackTime;
    private final HUDMotion motion = new HUDMotion();
    private final HUDMotion.Spring reveal = new HUDMotion.Spring(0);
    private final HUDMotion.Spring healthMotion = new HUDMotion.Spring(1);
    private final ComboSetting healthDisplaySetting = new ComboSetting("setting.health.display",
        "setting.health.display.description", Icon.FAVORITE, this,
        Arrays.asList("setting.health.display.text", "setting.health.display.bar"), "setting.health.display.bar");

    public TargetHUDMod() {
        super("mod.targethud.name", "mod.targethud.description", Icon.PERSON);
        instance = this;
        // Register once: toggling the mod must not accumulate global callbacks.
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (isEnabled() && world.isClientSide() && player instanceof LocalPlayer && entity instanceof Player target) {
                targetPlayer = target;
                lastAttackTime = System.currentTimeMillis();
            }
            return InteractionResult.PASS;
        });
    }

    public static TargetHUDMod getInstance() { return instance; }
    @Override public void onDisable() { super.onDisable(); targetPlayer = null; }

    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
        float dt = motion.deltaSeconds();
        boolean visible = HUDCore.isEditing || targetPlayer != null && client.level != null
            && client.level.getEntity(targetPlayer.getId()) != null
            && System.currentTimeMillis() - lastAttackTime < 10000;
        float amount = reveal.update(visible ? 1 : 0, dt, reducedMotion());
        if (amount < 0.01f) { position.setSize(0, 0); return; }
        Player player = HUDCore.isEditing ? client.player : targetPlayer;
        float health = player == null ? 20 : Math.max(0, HealthUtils.getActualHealth(player));
        float maxHealth = player == null ? 20 : Math.max(1, player.getMaxHealth());
        float fraction = Math.max(0, Math.min(1, health / maxHealth));
        float progress = healthMotion.update(fraction, dt, reducedMotion());
        float height = HEIGHT * amount;
        begin();
        try {
            drawBackground(getX(), getY(), WIDTH, height);
            Skia.clip(getX(), getY(), WIDTH, height, getRadius());
            drawAvatar(player, getX() + HUDTokens.PADDING, getY() + 12);
            float x = getX() + 48;
            String name = player == null ? "Player" : player.getName().getString();
            Skia.drawText(Skia.getLimitText(name, HUDTokens.title(), WIDTH - 56), x, getY() + 9,
                colors().text(), HUDTokens.title());
            String value = String.format(Locale.ROOT, "%.1f / %.1f HP", health, maxHealth);
            var color = fraction <= 0.25f ? colors().danger() : colors().secondaryText();
            Skia.drawFullCenteredText(Icon.FAVORITE, x + 5, getY() + 30, color, HUDTokens.icon());
            Skia.drawText(value, x + 13, getY() + 25, color, HUDTokens.label());
            if (healthDisplaySetting.getOption().equals("setting.health.display.bar")) {
                float barWidth = WIDTH - 56;
                Skia.drawRoundedRect(x, getY() + 42, barWidth, 6, 3, colors().track());
                if (progress > 0.001f)
                    Skia.drawRoundedRect(x, getY() + 42, barWidth * progress, 6, 3,
                        fraction <= 0.25f ? colors().danger() : colors().accent());
            }
        } finally { finish(); }
        position.setSize(WIDTH, height);
    };

    private void drawAvatar(Player player, float x, float y) {
        Skia.drawRoundedRect(x, y, AVATAR, AVATAR, HUDTokens.COMPACT_RADIUS, colors().accentContainer());
        if (player instanceof AbstractClientPlayer clientPlayer && clientPlayer.getSkin() != null) {
            File skin = SkinUtils.getSkin(clientPlayer.getSkin().body().texturePath());
            if (skin != null && skin.exists()) {
                Skia.drawPlayerHead(skin, x, y, AVATAR, AVATAR, HUDTokens.COMPACT_RADIUS);
                Skia.drawOutline(x, y, AVATAR, AVATAR, HUDTokens.COMPACT_RADIUS, 0.75f, colors().outline());
                return;
            }
        }
        Skia.drawFullCenteredText(Icon.PERSON, x + AVATAR / 2, y + AVATAR / 2,
            colors().onAccentContainer(), HUDTokens.icon());
    }
}
