package cn.pupperclient.management.mod.api.hud;

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
        float desiredWidth = Math.max(108, Skia.getTextBounds(getName(), HUDTokens.title()).getWidth() + 44);
        for (Entry entry : entries.values()) {
            Row row = entry.row;
            desiredWidth = Math.max(desiredWidth, Skia.getTextBounds(row.label(), HUDTokens.body()).getWidth()
                + Skia.getTextBounds(row.detail(), HUDTokens.label()).getWidth() + 52);
        }
        float width = widthMotion.update(Math.min(maxWidth, desiredWidth), dt, reduced);
        float height = HUDTokens.PADDING * 2 + HUDTokens.ROW_HEIGHT;
        for (Entry entry : entries.values()) height += HUDTokens.ROW_HEIGHT * entry.visibility;
        if (entries.isEmpty()) height += HUDTokens.ROW_HEIGHT;

        position.setSize(width, height);
        begin();
        try {
            if (backgroundSetting.isEnabled() || entries.isEmpty()) drawBackground(getX(), getY(), width, height);
            else drawBackground(getX(), getY(), width, HUDTokens.PADDING * 2 + HUDTokens.ROW_HEIGHT);
            Skia.drawHeightCenteredText(Skia.getLimitText(getName(), HUDTokens.title(), width - 48),
                getX() + HUDTokens.PADDING, getY() + 16, colors().text(), HUDTokens.title());
            Skia.drawRoundedRect(getX() + width - 28, getY() + 7, 20, 20, 7, colors().accentContainer());
            Skia.drawFullCenteredText(getIcon(), getX() + width - 18, getY() + 17,
                colors().onAccentContainer(), HUDTokens.icon());
            if (backgroundSetting.isEnabled() && !entries.isEmpty())
                Skia.drawRoundedRect(getX() + HUDTokens.PADDING, getY() + HUDTokens.PADDING + HUDTokens.ROW_HEIGHT - 1,
                    width - HUDTokens.PADDING * 2, 1, 0.5f, colors().outline());
            float y = getY() + HUDTokens.PADDING + HUDTokens.ROW_HEIGHT;
            boolean right = modeSetting.getOption().equals("setting.right");
            for (Entry entry : entries.values()) {
                float rowHeight = HUDTokens.ROW_HEIGHT * entry.visibility;
                Skia.save();
                try {
                    Skia.clip(getX(), y, width, rowHeight, 0);
                    float offset = (right ? 1 : -1) * 8 * (1 - entry.visibility);
                    if (!backgroundSetting.isEnabled()) drawBackground(getX(), y, width, HUDTokens.ROW_HEIGHT);
                    Row row = entry.row;
                    float x = getX() + HUDTokens.PADDING + offset;
                    float centerY = y + HUDTokens.ROW_HEIGHT / 2;
                    Skia.drawFullCenteredText(row.icon(), x + 5, centerY,
                        row.urgent() ? colors().danger() : colors().accent(), HUDTokens.icon());
                    String detail = Skia.getLimitText(row.detail(), HUDTokens.label(), width / 3);
                    float detailWidth = Skia.getTextBounds(detail, HUDTokens.label()).getWidth();
                    Skia.drawHeightCenteredText(Skia.getLimitText(row.label(), HUDTokens.body(),
                        Math.max(8, width - 44 - detailWidth)), x + 16, centerY, colors().text(), HUDTokens.body());
                    Skia.drawHeightCenteredText(detail, getX() + width - HUDTokens.PADDING - detailWidth + offset,
                        centerY, row.urgent() ? colors().danger() : colors().secondaryText(), HUDTokens.label());
                } finally { Skia.restore(); }
                y += rowHeight;
            }
            if (entries.isEmpty())
                Skia.drawHeightCenteredText(I18n.get("hud.empty"), getX() + HUDTokens.PADDING,
                    y + 10, colors().secondaryText(), HUDTokens.label());
        } finally { finish(); }
    }

    @Override public void onDisable() { super.onDisable(); entries.clear(); }
    private static final class Entry {
        Row row;
        boolean active;
        float visibility;
        Entry(Row row) { this.row = row; }
    }
}
