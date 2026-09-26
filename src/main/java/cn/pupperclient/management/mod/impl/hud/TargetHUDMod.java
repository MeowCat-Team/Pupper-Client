package cn.pupperclient.management.mod.impl.hud;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.gui.edithud.api.HUDCore;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.management.mod.api.hud.design.HUDMotion;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.management.mod.settings.impl.ComboSetting;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.color.ColorUtils;
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
    private Appearance appearance;
    private Appearance outgoingAppearance;
    private float appearanceReveal = 1;
    private float lastHealth = 20;
    private float lastMaxHealth = 20;
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
    @Override public void onDisable() {
        super.onDisable();
        targetPlayer = null;
        appearance = null;
        outgoingAppearance = null;
        appearanceReveal = 1;
    }

    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
        float dt = motion.deltaSeconds();
        boolean visible = HUDCore.isEditing || targetPlayer != null && client.level != null
            && client.level.getEntity(targetPlayer.getId()) != null
            && System.currentTimeMillis() - lastAttackTime < 10000;
        boolean reduced = reducedMotion();
        float amount = reveal.update(visible ? 1 : 0, dt, reduced);
        if (amount < 0.01f) { position.setSize(0, 0); return; }
        Player player = visible ? (HUDCore.isEditing ? client.player : targetPlayer) : null;
        if (visible) {
            Appearance next = snapshot(player);
            if (appearance == null) appearance = next;
            else if (!appearance.equals(next)) {
                outgoingAppearance = appearance;
                appearance = next;
                appearanceReveal = 0;
            }
            lastHealth = player == null ? 20 : Math.max(0, HealthUtils.getActualHealth(player));
            lastMaxHealth = player == null ? 20 : Math.max(1, player.getMaxHealth());
        }
        appearanceReveal = HUDMotion.approach(appearanceReveal, 1, dt, reduced);
        if (appearanceReveal >= 0.99f) outgoingAppearance = null;
        Appearance current = appearance == null ? new Appearance(null, "Player", null) : appearance;
        float health = lastHealth;
        float maxHealth = lastMaxHealth;
        float fraction = Math.max(0, Math.min(1, health / maxHealth));
        float progress = healthMotion.update(fraction, dt, reduced);
        float height = HEIGHT * amount;
        position.setSize(WIDTH, height);
        begin();
        try {
            drawBackground(getX(), getY(), WIDTH, height);
            Skia.clip(getX(), getY(), WIDTH, height, Math.min(getRadius(), height / 2));
            float avatarX = getX() + HUDTokens.PADDING, avatarY = getY() + 12;
            if (outgoingAppearance != null) {
                drawAvatar(outgoingAppearance.skin(), avatarX, avatarY);
                Skia.save();
                try {
                    Skia.clip(avatarX, avatarY, AVATAR * appearanceReveal, AVATAR, 0);
                    drawAvatar(current.skin(), avatarX, avatarY);
                } finally { Skia.restore(); }
            } else drawAvatar(current.skin(), avatarX, avatarY);
            float x = getX() + 48;
            if (outgoingAppearance != null)
                Skia.drawText(Skia.getLimitText(outgoingAppearance.name(), HUDTokens.title(), WIDTH - 56),
                    x, getY() + 9 - 4 * appearanceReveal,
                    ColorUtils.applyAlpha(colors().text(), 1 - appearanceReveal), HUDTokens.title());
            float entering = outgoingAppearance == null ? 1 : appearanceReveal;
            Skia.drawText(Skia.getLimitText(current.name(), HUDTokens.title(), WIDTH - 56),
                x, getY() + 9 + 4 * (1 - entering),
                ColorUtils.applyAlpha(colors().text(), entering), HUDTokens.title());
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
    };

    private Appearance snapshot(Player player) {
        if (player == null) return new Appearance(null, "Player", null);
        File skin = null;
        if (player instanceof AbstractClientPlayer clientPlayer && clientPlayer.getSkin() != null) {
            skin = SkinUtils.getSkin(clientPlayer.getSkin().body().texturePath());
            if (skin != null && !skin.exists()) skin = null;
        }
        return new Appearance(player.getUUID(), player.getName().getString(), skin);
    }

    private void drawAvatar(File skin, float x, float y) {
        Skia.drawRoundedRect(x, y, AVATAR, AVATAR, HUDTokens.COMPACT_RADIUS, colors().accentContainer());
        if (skin != null && skin.exists()) {
            Skia.drawPlayerHead(skin, x, y, AVATAR, AVATAR, HUDTokens.COMPACT_RADIUS);
            Skia.drawOutline(x, y, AVATAR, AVATAR, HUDTokens.COMPACT_RADIUS, 0.75f, colors().outline());
            return;
        }
        Skia.drawFullCenteredText(Icon.PERSON, x + AVATAR / 2, y + AVATAR / 2,
            colors().onAccentContainer(), HUDTokens.icon());
    }

    private record Appearance(UUID id, String name, File skin) {}
}
