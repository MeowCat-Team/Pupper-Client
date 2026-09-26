package cn.pupperclient.management.mod.api.hud;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import cn.pupperclient.management.mod.api.hud.design.HUDColors;
import cn.pupperclient.management.mod.api.hud.design.HUDMotion;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.skia.Skia;
import io.github.humbleui.skija.Font;

/** Stable line boxes with interruptible width, row, and visibility motion. */
public abstract class SimpleListHUDMod extends HUDMod {
    private static final Pattern FORMAT_CODE = Pattern.compile("§[0-9a-fk-orA-FK-OR]");
    private static final Pattern NUMERIC_PART = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private final HUDMotion motion = new HUDMotion();
    private final HUDMotion.Spring widthMotion = new HUDMotion.Spring(0);
    private final Map<String, Line> entries = new LinkedHashMap<>();
    private String title = "";
    private String outgoingTitle;
    private String icon = "";
    private float titleProgress = 1;
    private float headerVisibility;
    private float reveal;
    private boolean initialized;

    public SimpleListHUDMod(String name, String description, String icon) { super(name, description, icon); }

    protected void draw() {
        float dt = motion.deltaSeconds();
        boolean reduced = reducedMotion();
        List<String> source = getText();
        List<String> text = new ArrayList<>();
        if (source != null) {
            for (String line : source)
                text.add(FORMAT_CODE.matcher(Objects.toString(line, "")).replaceAll(""));
        }
        boolean visible = !text.isEmpty();
        if (visible) {
            String nextTitle = text.getFirst();
            if (!Objects.equals(title, nextTitle)) {
                boolean numericUpdate = containsDigit(title) && containsDigit(nextTitle)
                    && NUMERIC_PART.matcher(title).replaceAll("#")
                        .equals(NUMERIC_PART.matcher(nextTitle).replaceAll("#"));
                outgoingTitle = initialized && !reduced && !numericUpdate
                    ? (titleProgress < 0.5f && outgoingTitle != null ? outgoingTitle : title) : null;
                titleProgress = outgoingTitle == null ? 1 : 0;
                title = nextTitle;
            }
            icon = Objects.toString(getIcon(), "");
        }
        titleProgress = HUDMotion.approach(titleProgress, 1, dt, reduced);
        if (titleProgress >= 0.99f) {
            titleProgress = 1;
            outgoingTitle = null;
        }
        headerVisibility = HUDMotion.approach(headerVisibility, visible ? 1 : 0,
            dt, reduced || !initialized);

        entries.values().forEach(entry -> entry.active = false);
        Map<String, Integer> occurrence = new HashMap<>();
        for (int i = 1; i < text.size(); i++) {
            String line = text.get(i);
            // Numeric scores change frequently; their row identity must remain stable.
            String base = NUMERIC_PART.matcher(line).replaceAll("#");
            String id = base + '\0' + occurrence.merge(base, 1, Integer::sum);
            float slot = HUDTokens.PADDING + HUDTokens.ROW_HEIGHT + (i - 1) * HUDTokens.LINE_HEIGHT;
            Line entry = entries.computeIfAbsent(id, key -> new Line(line, slot, !initialized || reduced));
            entry.text = line;
            entry.slot = slot;
            entry.active = true;
        }
        for (Line entry : entries.values())
            entry.visibility = HUDMotion.approach(entry.visibility, entry.active ? 1 : 0,
                dt, reduced || !initialized);
        entries.values().removeIf(entry -> !entry.active && entry.visibility < 0.01f);
        for (Line entry : entries.values())
            entry.renderSlot = entry.yMotion.update(entry.slot, dt, reduced || !initialized);

        float available = Math.max(40, Math.min(300,
            client.getWindow().getGuiScaledWidth() / Math.max(0.2f, position.getScale()) - 32));
        float desiredWidth = 0;
        if (visible || headerVisibility > 0.01f) {
            desiredWidth = Math.min(available, Skia.getTextBounds(title, HUDTokens.title()).getWidth() + 24);
            for (Line entry : entries.values())
                desiredWidth = Math.max(desiredWidth,
                    Math.min(available, Skia.getTextBounds(entry.text, HUDTokens.body()).getWidth()));
            desiredWidth += HUDTokens.PADDING * 2;
        }
        float width = Math.max(0, Math.min(available + HUDTokens.PADDING * 2,
            widthMotion.update(desiredWidth, dt, reduced || !initialized)));
        float height = (HUDTokens.PADDING * 2 + HUDTokens.ROW_HEIGHT) * headerVisibility;
        for (Line entry : entries.values()) height += HUDTokens.LINE_HEIGHT * entry.visibility;
        // A row below a departing middle line moves upward on its own spring.
        // Keep the panel tall enough for that live row until it reaches its slot.
        float headerHeight = HUDTokens.PADDING * 2 + HUDTokens.ROW_HEIGHT;
        for (Line entry : entries.values()) {
            if (entry.active)
                height = Math.max(height, headerHeight * headerVisibility
                    + Math.max(0, entry.renderSlot + HUDTokens.LINE_HEIGHT + HUDTokens.PADDING
                        - headerHeight) * entry.visibility);
        }
        reveal = HUDMotion.approach(reveal, visible || !entries.isEmpty() ? 1 : 0, dt, reduced);
        height *= reveal;
        initialized = true;
        if (width < 0.5f || height < 0.5f) {
            position.setSize(0, 0);
            if (!visible && entries.isEmpty()) reveal = 0;
            return;
        }
        position.setSize(width, height);

        begin();
        try {
            float x = getX(), y = getY();
            float radius = Math.min(getRadius(), Math.min(width, height) / 2);
            getDesign().drawBackground(x, y, width, height, radius);
            Skia.save();
            try {
                Skia.clip(x, y, width, height, radius);
                HUDColors palette = colors();
                float innerWidth = Math.max(8, width - HUDTokens.PADDING * 2);
                if (width > HUDTokens.PADDING * 2)
                    Skia.drawRoundedRect(x + HUDTokens.PADDING,
                        y + HUDTokens.PADDING + HUDTokens.ROW_HEIGHT - 2,
                        width - HUDTokens.PADDING * 2, 1, 0.5f,
                        fade(palette.outline(), headerVisibility));
                if (!icon.isBlank())
                    Skia.drawFullCenteredText(icon, x + width - HUDTokens.PADDING - 6,
                        y + HUDTokens.PADDING + 7, fade(palette.accent(), headerVisibility), HUDTokens.icon());
                if (outgoingTitle != null)
                    drawTitle(outgoingTitle, innerWidth, palette, headerVisibility * (1 - titleProgress));
                drawTitle(title, innerWidth, palette, headerVisibility * titleProgress);

                // Draw departing rows first so live values remain crisp when slots overlap.
                for (Line entry : entries.values()) if (!entry.active) drawLine(entry, innerWidth, palette);
                for (Line entry : entries.values()) if (entry.active) drawLine(entry, innerWidth, palette);
            } finally { Skia.restore(); }
        } finally { finish(); }
    }

    private void drawTitle(String value, float innerWidth, HUDColors palette, float opacity) {
        if (opacity <= 0) return;
        Skia.drawHeightCenteredText(Skia.getLimitText(value, HUDTokens.title(), Math.max(8, innerWidth - 24)),
            getX() + HUDTokens.PADDING, getY() + HUDTokens.PADDING + 7,
            fade(palette.text(), opacity), HUDTokens.title());
    }

    private void drawLine(Line entry, float innerWidth, HUDColors palette) {
        if (entry.visibility <= 0) return;
        Font font = HUDTokens.body();
        Skia.drawHeightCenteredText(Skia.getLimitText(entry.text, font, innerWidth),
            getX() + HUDTokens.PADDING, getY() + entry.renderSlot + 7,
            fade(palette.secondaryText(), entry.visibility), font);
    }

    private static Color fade(Color color, float opacity) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
            Math.round(color.getAlpha() * Math.max(0, Math.min(1, opacity))));
    }

    private static boolean containsDigit(String value) {
        return value.codePoints().anyMatch(Character::isDigit);
    }

    @Override public void onDisable() {
        super.onDisable();
        entries.clear();
        initialized = false;
        title = icon = "";
        outgoingTitle = null;
        titleProgress = 1;
        headerVisibility = 0;
        reveal = 0;
    }

    private static final class Line {
        String text;
        float slot;
        float renderSlot;
        float visibility;
        boolean active;
        final HUDMotion.Spring yMotion;

        Line(String text, float slot, boolean immediate) {
            this.text = text;
            this.slot = slot;
            visibility = immediate ? 1 : 0;
            yMotion = new HUDMotion.Spring(slot + (immediate ? 0 : 4));
        }
    }

    public abstract List<String> getText();
    public abstract String getIcon();
}
