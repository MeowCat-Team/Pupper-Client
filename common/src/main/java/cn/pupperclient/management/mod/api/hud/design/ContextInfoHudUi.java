package cn.pupperclient.management.mod.api.hud.design;

import cn.pupperclient.skia.Skia;
import io.github.humbleui.skija.ClipMode;
import java.util.ArrayList;
import java.util.List;

/** The same compact card geometry is used for Minecraft icon extraction and Blaze3D text. */
public final class ContextInfoHudUi {
    public static final float ICON_X = 10, ICON_Y = 10, ICON_SIZE = 20, TEXT_X = 40;
    public record Layout(float width, float height, String title, List<String> lines) { }
    private ContextInfoHudUi() { }

    public static Layout layout(String title, List<String> details, float maxWidth) {
        float available = Math.max(52, maxWidth), textLimit = available - TEXT_X - 10;
        var lines = new ArrayList<String>();
        for (String detail : details) {
            String current = "";
            for (String token : detail.split(" · ")) {
                String candidate = current.isEmpty() ? token : current + " · " + token;
                if (!current.isEmpty() && HUDTokens.body().measureText(candidate).getWidth() > textLimit) {
                    lines.add(Skia.getLimitText(current, HUDTokens.body(), textLimit)); current = token;
                } else current = candidate;
            }
            if (!current.isEmpty()) lines.add(Skia.getLimitText(current, HUDTokens.body(), textLimit));
        }
        String heading = Skia.getLimitText(title, HUDTokens.title(), textLimit);
        float textWidth = HUDTokens.title().measureText(heading).getWidth();
        for (var line : lines) textWidth = Math.max(textWidth, HUDTokens.body().measureText(line).getWidth());
        return new Layout(Math.min(available, Math.max(120, textWidth + TEXT_X + 10)),
            Math.max(40, 26 + lines.size() * 14), heading, List.copyOf(lines));
    }
    public static void draw(float x, float y, Layout layout, String fallbackIcon, boolean extractedIcon,
            HUDDesign design, HUDColors colors) {
        Skia.save();
        try {
            // Item sprites have already been extracted by Minecraft; leave their pixels unobscured.
            if (extractedIcon) Skia.clip(x + ICON_X, y + ICON_Y, ICON_SIZE, ICON_SIZE, 0, ClipMode.DIFFERENCE);
            design.drawBackground(x, y, layout.width(), layout.height(), HUDTokens.RADIUS);
        } finally { Skia.restore(); }
        if (!extractedIcon) HUDText.drawFullCenteredText(fallbackIcon, x + ICON_X + ICON_SIZE / 2,
            y + ICON_Y + ICON_SIZE / 2, colors.accent(), cn.pupperclient.skia.font.Fonts.getIcon(20));
        HUDText.drawHeightCenteredText(layout.title(), x + TEXT_X, y + (layout.lines().isEmpty() ? 20 : 15), colors.text(), HUDTokens.title());
        for (int i = 0; i < layout.lines().size(); i++) HUDText.drawHeightCenteredText(layout.lines().get(i),
            x + TEXT_X, y + 30 + i * 14, colors.secondaryText(), HUDTokens.body());
    }
}
