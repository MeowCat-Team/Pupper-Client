package cn.pupperclient.management.mod.api.hud;

import java.awt.Color;
import java.util.Objects;
import java.util.regex.Pattern;
import cn.pupperclient.management.mod.api.hud.design.HUDColors;
import cn.pupperclient.management.mod.api.hud.design.HUDMotion;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;
import io.github.humbleui.skija.Font;

/** Compact metric with a quiet surface, paired icon badge, and consistent baseline. */
public abstract class SimpleHUDMod extends HUDMod {
    private static final Pattern NUMERIC_PART = Pattern.compile("\\d+(?:[.,]\\d+)*");
    protected BooleanSetting iconSetting = new BooleanSetting("setting.icon", "setting.icon.description",
        Icon.NEW_RELEASES, this, true);

    private final HUDMotion motion = new HUDMotion();
    private final HUDMotion.Spring widthMotion = new HUDMotion.Spring(0);
    private Content shown;
    private Content outgoing;
    private float contentProgress = 1;
    private float reveal;
    private boolean initialized;

    public SimpleHUDMod(String name, String description, String icon) { super(name, description, icon); }

    protected void draw() {
        float dt = motion.deltaSeconds();
        boolean reduced = reducedMotion();
        Font font = getTextFont(HUDTokens.BODY_SIZE);
        float maxWidth = Math.max(64, Math.min(280,
            client.getWindow().getGuiScaledWidth() / Math.max(0.5f, position.getScale()) - 40));
        Content current = content(font, maxWidth);

        if (!initialized) {
            shown = current;
            outgoing = null;
            contentProgress = 1;
        } else if (!sameVisual(shown, current)) {
            // Rapidly changing numbers are rendered immediately. Crossfading each FPS or
            // coordinate update would leave the metric permanently dim and hard to read.
            boolean numericUpdate = !Objects.equals(shown.value, current.value)
                && hasDigit(shown.value) && hasDigit(current.value)
                && NUMERIC_PART.matcher(shown.value).replaceAll("#")
                    .equals(NUMERIC_PART.matcher(current.value).replaceAll("#"));
            boolean structureChanged = !Objects.equals(shown.label, current.label)
                || !Objects.equals(shown.unit, current.unit)
                || !Objects.equals(shown.icon, current.icon)
                || shown.hasIcon != current.hasIcon || shown.urgent != current.urgent;
            if (!reduced && (!numericUpdate || structureChanged)) {
                outgoing = contentProgress < 0.5f && outgoing != null ? outgoing : shown;
                contentProgress = 0;
            } else {
                outgoing = null;
                contentProgress = 1;
            }
            shown = current;
        } else {
            // Width can still change when the window or HUD scale changes.
            shown = current;
        }

        contentProgress = HUDMotion.approach(contentProgress, 1, dt, reduced);
        if (contentProgress >= 0.99f) {
            contentProgress = 1;
            outgoing = null;
        }
        reveal = HUDMotion.approach(reveal, 1, dt, reduced);
        float width = Math.max(0, Math.min(maxWidth,
            widthMotion.update(current.width, dt, reduced || !initialized))) * reveal;
        float height = HUDTokens.CHIP_HEIGHT;
        initialized = true;
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
                if (outgoing != null) drawContent(outgoing, font, palette, 1 - contentProgress);
                drawContent(shown, font, palette, contentProgress);
            } finally { Skia.restore(); }
        } finally { finish(); }
    }

    private Content content(Font font, float maxWidth) {
        String text = Objects.toString(getText(), "");
        String unit = Objects.toString(getUnit(), "");
        if (!unit.isBlank() && text.endsWith(" " + unit))
            text = text.substring(0, text.length() - unit.length() - 1);
        String icon = Objects.toString(getIcon(), "");
        boolean hasIcon = !icon.isBlank() && iconSetting.isEnabled();
        float badge = 18;
        float unitWidth = unit.isBlank() ? 0 : Skia.getTextBounds(unit, HUDTokens.label()).getWidth() + HUDTokens.GAP;
        float textLimit = Math.max(8, maxWidth - HUDTokens.PADDING * 2
            - (hasIcon ? badge + HUDTokens.GAP : 0) - unitWidth);

        // A single "Label: value" pair gets separate M3 label/value emphasis.
        // Multiple colons (coordinates, addresses) remain one intact value.
        int separator = text.indexOf(": ");
        boolean hasLabel = separator > 0 && separator < 24 && text.indexOf(": ", separator + 2) < 0;
        String label = hasLabel ? Skia.getLimitText(text.substring(0, separator), HUDTokens.label(), textLimit * 0.44f) : "";
        String value = hasLabel ? text.substring(separator + 2) : text;
        float labelWidth = label.isBlank() ? 0 : Skia.getTextBounds(label, HUDTokens.label()).getWidth() + HUDTokens.GAP;
        value = Skia.getLimitText(value, font, Math.max(8, textLimit - labelWidth));
        float valueWidth = Skia.getTextBounds(value, font).getWidth();
        float width = HUDTokens.PADDING * 2 + labelWidth + valueWidth + unitWidth
            + (hasIcon ? badge + HUDTokens.GAP : 0);
        return new Content(label, value, unit, icon, hasIcon, isUrgent(), labelWidth, valueWidth, width);
    }

    private void drawContent(Content content, Font font, HUDColors palette, float opacity) {
        if (opacity <= 0) return;
        float x = getX() + HUDTokens.PADDING;
        float centerY = getY() + HUDTokens.CHIP_HEIGHT / 2;
        if (content.hasIcon) {
            Skia.drawRoundedRect(x - 3, getY() + 3, 18, 18, 6, fade(palette.accentContainer(), opacity));
            Skia.drawFullCenteredText(content.icon, x + 6, centerY,
                fade(palette.onAccentContainer(), opacity), getIconFont(HUDTokens.ICON_SIZE));
            x += 18 + HUDTokens.GAP;
        }
        if (!content.label.isBlank()) {
            Skia.drawHeightCenteredText(content.label, x, centerY,
                fade(palette.secondaryText(), opacity), HUDTokens.label());
            x += content.labelWidth;
        }
        Skia.drawHeightCenteredText(content.value, x, centerY,
            fade(content.urgent ? palette.danger() : palette.text(), opacity), font);
        if (!content.unit.isBlank())
            Skia.drawHeightCenteredText(content.unit, x + content.valueWidth + HUDTokens.GAP,
                centerY, fade(palette.secondaryText(), opacity), HUDTokens.label());
    }

    private static Color fade(Color color, float opacity) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
            Math.round(color.getAlpha() * Math.max(0, Math.min(1, opacity))));
    }

    private static boolean hasDigit(String text) {
        return text.codePoints().anyMatch(Character::isDigit);
    }

    private static boolean sameVisual(Content first, Content second) {
        return Objects.equals(first.label, second.label) && Objects.equals(first.value, second.value)
            && Objects.equals(first.unit, second.unit) && Objects.equals(first.icon, second.icon)
            && first.hasIcon == second.hasIcon && first.urgent == second.urgent;
    }

    @Override public void onDisable() {
        super.onDisable();
        initialized = false;
        shown = outgoing = null;
        contentProgress = 1;
        reveal = 0;
    }

    private record Content(String label, String value, String unit, String icon, boolean hasIcon,
                           boolean urgent, float labelWidth, float valueWidth, float width) {}

    @Override public float getRadius() { return HUDTokens.CHIP_HEIGHT / 2; }
    public abstract String getText();
    public abstract String getIcon();
    protected String getUnit() { return ""; }
    protected boolean isUrgent() { return false; }
    protected Font getTextFont(float size) { return HUDTokens.value(); }
    protected Font getIconFont(float size) { return HUDTokens.icon(); }
}
