package cn.pupperclient.management.mod.api.hud;

import java.awt.Color;
import java.util.*;
import cn.pupperclient.gui.edithud.api.HUDCore;
import cn.pupperclient.management.mod.api.hud.design.HUDMotion;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.management.mod.settings.impl.ComboSetting;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.language.I18n;

/** Shared list layout and interruptible animation for modules and effects. */
public abstract class AnimatedListHUDMod extends HUDMod {
    private static final float HORIZONTAL_PADDING = 12;
    private static final float VERTICAL_PADDING = 10;
    private static final float HEADER_HEIGHT = 24;
    private static final float ROW_HEIGHT = 24;
    private static final float SECTION_GAP = 6;
    private static final float HEADER_ICON_SIZE = 22;

    private final BooleanSetting backgroundSetting = new BooleanSetting("setting.background",
        "setting.background1.description", Icon.IMAGE, this, true);
    private final ComboSetting modeSetting = new ComboSetting("setting.mode", "setting.mode.description",
        Icon.ALIGN_HORIZONTAL_RIGHT, this, Arrays.asList("setting.right", "setting.left"), "setting.right");
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final HUDMotion motion = new HUDMotion();
    private final HUDMotion.Spring widthMotion = new HUDMotion.Spring(100);

    protected AnimatedListHUDMod(String name, String description, String icon) { super(name, description, icon); }
    public record Row(String id, String label, String detail, String icon, boolean urgent) {}
    protected abstract List<Row> rows();

    protected float minimumListWidth() { return 120; }
    protected float listRowHeight() { return ROW_HEIGHT; }

    protected final void drawList() {
        float dt = motion.deltaSeconds();
        boolean reduced = reducedMotion();
        entries.values().forEach(entry -> entry.active = false);
        for (Row row : rows()) {
            Entry entry = entries.computeIfAbsent(row.id(), key -> new Entry(row));
            entry.row = row;
            entry.active = true;
        }
        for (Entry entry : entries.values())
            entry.visibility = HUDMotion.approach(entry.visibility, entry.active ? 1 : 0, dt, reduced);
        entries.values().removeIf(entry -> !entry.active && entry.visibility < 0.01f);
        if (entries.isEmpty() && !HUDCore.isEditing) { position.setSize(0, 0); return; }

        float maxWidth = Math.max(80, Math.min(260, client.getWindow().getGuiScaledWidth() / position.getScale() - 32));
        float desiredWidth = Math.max(minimumListWidth(), Skia.getTextBounds(getName(), HUDTokens.title()).getWidth()
            + HORIZONTAL_PADDING * 2 + HEADER_ICON_SIZE + 8);
        for (Entry entry : entries.values()) {
            Row row = entry.row;
            desiredWidth = Math.max(desiredWidth, Skia.getTextBounds(row.label(), HUDTokens.body()).getWidth()
                + Skia.getTextBounds(row.detail(), HUDTokens.label()).getWidth()
                + HORIZONTAL_PADDING * 2 + (hasIcon(row) ? 28 : 8));
        }
        float width = widthMotion.update(Math.min(maxWidth, desiredWidth), dt, reduced);
        float itemHeight = listRowHeight();
        float height = VERTICAL_PADDING * 2 + HEADER_HEIGHT + SECTION_GAP;
        for (Entry entry : entries.values()) height += itemHeight * entry.visibility;
        if (entries.isEmpty()) height += itemHeight;

        position.setSize(width, height);
        begin();
        try {
            if (backgroundSetting.isEnabled() || entries.isEmpty()) drawBackground(getX(), getY(), width, height);
            else drawBackground(getX(), getY(), width, VERTICAL_PADDING + HEADER_HEIGHT + SECTION_GAP / 2);
            float headerCenterY = getY() + VERTICAL_PADDING + HEADER_HEIGHT / 2;
            Skia.drawHeightCenteredText(Skia.getLimitText(getName(), HUDTokens.title(),
                    width - HORIZONTAL_PADDING * 2 - HEADER_ICON_SIZE - 8),
                getX() + HORIZONTAL_PADDING, headerCenterY, colors().text(), HUDTokens.title());
            float iconX = getX() + width - HORIZONTAL_PADDING - HEADER_ICON_SIZE;
            float iconY = headerCenterY - HEADER_ICON_SIZE / 2;
            Skia.drawRoundedRect(iconX, iconY, HEADER_ICON_SIZE, HEADER_ICON_SIZE, 8, colors().raised());
            Skia.drawFullCenteredText(getIcon(), iconX + HEADER_ICON_SIZE / 2, headerCenterY,
                colors().secondaryText(), HUDTokens.icon());
            if (backgroundSetting.isEnabled() && !entries.isEmpty()) {
                Color outline = colors().outline();
                Color divider = new Color(outline.getRed(), outline.getGreen(), outline.getBlue(), 92);
                Skia.drawRoundedRect(getX() + HORIZONTAL_PADDING,
                    getY() + VERTICAL_PADDING + HEADER_HEIGHT + SECTION_GAP / 2,
                    width - HORIZONTAL_PADDING * 2, 1, 0.5f, divider);
            }
            float y = getY() + VERTICAL_PADDING + HEADER_HEIGHT + SECTION_GAP;
            boolean right = modeSetting.getOption().equals("setting.right");
            for (Entry entry : entries.values()) {
                float rowHeight = itemHeight * entry.visibility;
                Skia.save();
                try {
                    Skia.clip(getX(), y, width, rowHeight, 0);
                    float offset = (right ? 1 : -1) * 8 * (1 - entry.visibility);
                    if (!backgroundSetting.isEnabled()) drawBackground(getX(), y + 2, width, itemHeight - 4);
                    Row row = entry.row;
                    float x = getX() + HORIZONTAL_PADDING + offset;
                    float centerY = y + itemHeight / 2;
                    boolean hasIcon = hasIcon(row);
                    if (hasIcon) Skia.drawFullCenteredText(row.icon(), x + 5, centerY,
                        row.urgent() ? colors().danger() : colors().secondaryText(), HUDTokens.icon());
                    String detail = Skia.getLimitText(row.detail(), HUDTokens.label(), width / 3);
                    float detailWidth = Skia.getTextBounds(detail, HUDTokens.label()).getWidth();
                    Skia.drawHeightCenteredText(Skia.getLimitText(row.label(), HUDTokens.body(),
                        Math.max(8, width - HORIZONTAL_PADDING * 2 - (hasIcon ? 28 : 8) - detailWidth)),
                        x + (hasIcon ? 20 : 0), centerY, colors().text(), HUDTokens.body());
                    Skia.drawHeightCenteredText(detail, getX() + width - HORIZONTAL_PADDING - detailWidth + offset,
                        centerY, row.urgent() ? colors().danger() : colors().secondaryText(), HUDTokens.label());
                } finally { Skia.restore(); }
                y += rowHeight;
            }
            if (entries.isEmpty())
                Skia.drawHeightCenteredText(I18n.get("hud.empty"), getX() + HORIZONTAL_PADDING,
                    y + itemHeight / 2, colors().secondaryText(), HUDTokens.label());
        } finally { finish(); }
    }

    private static boolean hasIcon(Row row) { return row.icon() != null && !row.icon().isBlank(); }

    @Override public void onDisable() { super.onDisable(); entries.clear(); }
    private static final class Entry {
        Row row;
        boolean active;
        float visibility;
        Entry(Row row) { this.row = row; }
    }
}
