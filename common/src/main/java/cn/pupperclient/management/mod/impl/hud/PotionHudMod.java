package cn.pupperclient.management.mod.impl.hud;

import java.util.Comparator;
import java.util.List;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.AnimatedListHUDMod;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.misc.RomanConverter;
import net.minecraft.world.effect.MobEffectInstance;

public class PotionHudMod extends AnimatedListHUDMod {
    private static PotionHudMod instance;
    public PotionHudMod() {
        super("mod.potionhud.name", "mod.potionhud.description", Icon.SCIENCE);
        instance = this;
    }
    public static PotionHudMod getInstance() { return instance; }
    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> drawList();

    @Override protected List<Row> rows() {
        if (client.player == null) return List.of();
        return client.player.getActiveEffects().stream()
            .sorted(Comparator.comparing(effect -> effect.getEffect().value().getDescriptionId()))
            .map(effect -> {
                var type = effect.getEffect().value();
                int level = effect.getAmplifier() + 1;
                String label = type.getDisplayName().getString()
                    + (level > 1 ? " " + RomanConverter.intToRomanByPlace(level) : "");
                boolean urgent = !effect.isInfiniteDuration() && effect.getDuration() <= 200;
                return new Row(type.getDescriptionId(), label, duration(effect),
                    urgent ? Icon.TIMER : Icon.SCIENCE, urgent);
            }).toList();
    }

    private String duration(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) return "∞";
        int seconds = Math.max(0, effect.getDuration() / 20);
        return seconds >= 60 ? String.format("%d:%02d", seconds / 60, seconds % 60) : seconds + "s";
    }
}