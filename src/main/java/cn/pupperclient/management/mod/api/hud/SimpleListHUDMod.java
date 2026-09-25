package cn.pupperclient.management.mod.api.hud;

import java.util.List;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.skia.Skia;
import io.github.humbleui.skija.Font;

/** Stable line boxes prevent layout jitter when glyphs or numbers change. */
public abstract class SimpleListHUDMod extends HUDMod {
    public SimpleListHUDMod(String name, String description, String icon) { super(name, description, icon); }

    protected void draw() {
        List<String> lines = getText();
        if (lines.isEmpty()) { position.setSize(0, 0); return; }
        float available = Math.max(40, Math.min(300, client.getWindow().getGuiScaledWidth() / position.getScale() - 32));
        List<String> text = lines.stream().map(line -> line.replaceAll("§[0-9a-fk-orA-FK-OR]", "")).toList();
        float width = 0;
        for (int i = 0; i < text.size(); i++)
            width = Math.max(width, Math.min(available, Skia.getTextBounds(text.get(i), i == 0 ? HUDTokens.title() : HUDTokens.body()).getWidth()));
        width += HUDTokens.PADDING * 2;
        float height = HUDTokens.PADDING * 2 + HUDTokens.ROW_HEIGHT + (text.size() - 1) * HUDTokens.LINE_HEIGHT;
        begin();
        try {
            drawBackground(getX(), getY(), width, height);
            for (int i = 0; i < text.size(); i++) {
                Font font = i == 0 ? HUDTokens.title() : HUDTokens.body();
                float centerY = getY() + HUDTokens.PADDING + (i == 0 ? 7 : HUDTokens.ROW_HEIGHT + (i - 1) * HUDTokens.LINE_HEIGHT + 7);
                Skia.drawHeightCenteredText(Skia.getLimitText(text.get(i), font, available),
                    getX() + HUDTokens.PADDING, centerY, i == 0 ? colors().text() : colors().secondaryText(), font);
            }
        } finally { finish(); }
        position.setSize(width, height);
    }

    public abstract List<String> getText();
    public abstract String getIcon();
}