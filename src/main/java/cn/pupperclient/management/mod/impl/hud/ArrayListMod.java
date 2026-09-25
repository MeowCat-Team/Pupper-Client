package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.PupperClient;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.management.mod.impl.settings.ModMenuSettings;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.management.mod.settings.impl.ComboSetting;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ArrayListMod extends HUDMod {
    private static ArrayListMod instance;

    // Settings
    private final BooleanSetting backgroundSetting = new BooleanSetting("setting.background",
        "setting.background1.description", Icon.IMAGE, this, true);
    private final BooleanSetting hudSetting = new BooleanSetting("setting.hud",
        "setting.hud.description", Icon.DASHBOARD, this, false);
    private final BooleanSetting renderSetting = new BooleanSetting("setting.render",
        "setting.render.description", Icon.VISIBILITY, this, false);
    private final BooleanSetting playerSetting = new BooleanSetting("setting.player",
        "setting.player.description", Icon.PERSON, this, false);
    private final BooleanSetting otherSetting = new BooleanSetting("setting.other",
        "setting.other.description", Icon.MORE_HORIZ, this, false);
    private final ComboSetting modeSetting = new ComboSetting("setting.mode",
        "setting.mode.description", Icon.ALIGN_HORIZONTAL_RIGHT, this,
        Arrays.asList("setting.right", "setting.left"), "setting.right");

    // Design constants
    private static final float FONT_SIZE = 8.5f;
    private static final float ICON_SIZE = 9.5f;
    private static final float ROW_HEIGHT = 14f;
    private static final float HORIZONTAL_PADDING = 7f;
    private static final float VERTICAL_PADDING = 3f;
    private static final float ITEM_SPACING = 2f;
    private static final float ICON_TEXT_SPACING = 4f;
    private static final long ANIMATION_DURATION = 300L;

    // Animation states
    private final Map<String, ModAnimationState> animationStates = new ConcurrentHashMap<>();
    private final List<ModDisplayInfo> sortedDisplayMods = new ArrayList<>();

    public ArrayListMod() {
        super("mod.arraylist.name", "mod.arraylist.description", Icon.LIST);
        instance = this;
    }

    public static ArrayListMod getInstance() {
        return instance;
    }

    private final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
        draw();
    };

    private void draw() {
        try {
            begin();
            updateAnimationStates();
            drawArrayList();
        } catch (Exception e) {
            PupperClient.LOGGER.error("Error drawing ArrayListMod", e);
            position.setSize(100, 20);
        } finally {
            finish();
        }
    }

    private void updateAnimationStates() {
        long currentTime = System.currentTimeMillis();

        // Get current enabled mods
        List<ModDisplayInfo> enabledMods = getEnabledMods();

        // Update animation states for enabled mods
        for (ModDisplayInfo modInfo : enabledMods) {
            ModAnimationState state = animationStates.get(modInfo.originalName);
            if (state == null) {
                // New mod - start enter animation
                state = new ModAnimationState(modInfo, currentTime, AnimationType.ENTER);
                animationStates.put(modInfo.originalName, state);
            } else if (state.animationType == AnimationType.EXIT) {
                // Mod was exiting but got re-enabled - switch to enter animation
                state.animationType = AnimationType.ENTER;
                state.animationStartTime = currentTime;
                state.modInfo = modInfo;
            } else {
                // Update existing mod info
                state.modInfo = modInfo;
            }
        }

        // Handle mods that are no longer enabled
        Iterator<Map.Entry<String, ModAnimationState>> iterator = animationStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ModAnimationState> entry = iterator.next();
            String modName = entry.getKey();
            ModAnimationState state = entry.getValue();

            boolean stillEnabled = enabledMods.stream()
                .anyMatch(mod -> mod.originalName.equals(modName));

            if (!stillEnabled && state.animationType != AnimationType.EXIT) {
                // Start exit animation
                state.animationType = AnimationType.EXIT;
                state.animationStartTime = currentTime;
            }

            // Remove mods that have finished exit animation
            if (state.animationType == AnimationType.EXIT) {
                float progress = getAnimationProgress(currentTime, state.animationStartTime, ANIMATION_DURATION);
                if (progress >= 1.0f) {
                    iterator.remove();
                }
            }
        }

        // Update sorted display mods
        updateSortedDisplayMods();

    }

    private void updateSortedDisplayMods() {
        sortedDisplayMods.clear();

        // Add all active mods (both entering and stable)
        for (ModAnimationState state : animationStates.values()) {
            if (state.animationType != AnimationType.EXIT ||
                getAnimationProgress(System.currentTimeMillis(), state.animationStartTime, ANIMATION_DURATION) < 1.0f) {
                sortedDisplayMods.add(state.modInfo);
            }
        }

        // Sort by display name length (longest first) for consistent layout
        sortedDisplayMods.sort((a, b) -> Float.compare(b.totalWidth, a.totalWidth));
    }

    private void drawArrayList() {
        boolean isRightAligned = isRightAligned();

        // Calculate total height including title and all mod entries
        int totalEntries = 1 + sortedDisplayMods.size(); // 1 for title + mod entries
        float totalHeight = totalEntries * (ROW_HEIGHT + ITEM_SPACING) - ITEM_SPACING;

        // Calculate max width
        float maxWidth = calculateMaxWidth();

        float currentY = 0;

        drawTitleEntry(maxWidth, currentY, isRightAligned);
        currentY += ROW_HEIGHT + ITEM_SPACING;

        // Draw enabled mods
        for (int i = 0; i < sortedDisplayMods.size(); i++) {
            ModDisplayInfo modInfo = sortedDisplayMods.get(i);
            ModAnimationState state = animationStates.get(modInfo.originalName);

            if (state != null) {
                drawModEntry(modInfo, state, maxWidth, currentY, i, isRightAligned);
            }

            currentY += ROW_HEIGHT + ITEM_SPACING;
        }

        position.setSize(maxWidth, totalHeight);
    }

    private void drawTitleEntry(float maxWidth, float y, boolean isRightAligned) {
        String titleIcon = Icon.CODE;
        String titleText = "Active Modules";

        // Calculate widths
        float iconWidth = Skia.getTextBounds(titleIcon, Fonts.getIcon(9.7F)).getWidth();
        float textWidth = Skia.getTextBounds(titleText, Fonts.getRegular(FONT_SIZE)).getWidth();

        // Calculate background dimensions
        float totalWidth = iconWidth + ICON_TEXT_SPACING + textWidth + HORIZONTAL_PADDING * 2;

        // Calculate positions
        float bgX = getX() + (isRightAligned ? (maxWidth - totalWidth) : 0);
        float bgY = getY() + y;

        // Draw backgrounds
        if (backgroundSetting.isEnabled()) {
            drawRoundedBackground(bgX, bgY, totalWidth, 255);
        }

        // Draw title icon and text
        float iconX = bgX + HORIZONTAL_PADDING;
        float textX = iconX + iconWidth + ICON_TEXT_SPACING;
        float contentY = bgY + VERTICAL_PADDING;

        Color contentColor = getContentColor(255);
        Skia.drawText(titleIcon, iconX + 0.5F, contentY + 1.2F, contentColor, Fonts.getIcon(9.7F));
        Skia.drawText(titleText, textX, contentY, contentColor, Fonts.getRegular(FONT_SIZE));
    }

    private void drawModEntry(ModDisplayInfo modInfo, ModAnimationState state, float maxWidth,
                              float y, int index, boolean isRightAligned) {
        long currentTime = System.currentTimeMillis();
        float progress = getAnimationProgress(currentTime, state.animationStartTime, ANIMATION_DURATION);

        // Apply easing
        float easedProgress = easeOutCubic(progress);

        // For exit animations, we want to reverse the progress
        if (state.animationType == AnimationType.EXIT) {
            easedProgress = 1.0f - easedProgress;
        }

        // Calculate animation properties based on alignment
        float animationOffset;
        float alpha = 255 * easedProgress;

        if (isRightAligned) {
            // Right-aligned: animate from right to left
            animationOffset = maxWidth * (1 - easedProgress);
        } else {
            // Left-aligned: animate from left to right
            animationOffset = -maxWidth * (1 - easedProgress);
        }

        // Calculate positions
        float totalWidth = modInfo.totalWidth;
        float bgX = getX() + (isRightAligned ? (maxWidth - totalWidth) : 0) + animationOffset;
        float bgY = getY() + y;

        // Draw backgrounds with alpha
        if (backgroundSetting.isEnabled()) {
            drawRoundedBackground(bgX, bgY, totalWidth, (int) alpha);
        }

        // Draw content with alpha
        Color contentColor = getContentColor((int) alpha);

        // Draw check icon and module name
        float iconX = bgX + HORIZONTAL_PADDING;
        float textX = iconX + modInfo.iconWidth + ICON_TEXT_SPACING;
        float contentY = bgY + VERTICAL_PADDING;

        Skia.drawText(Icon.CHECK, iconX, contentY + 2, contentColor, Fonts.getIcon(ICON_SIZE));
        Skia.drawText(modInfo.displayName, textX, contentY, contentColor, Fonts.getRegular(FONT_SIZE));
    }

    private void drawRoundedBackground(float x, float y, float width, int alpha) {
        float radius = ROW_HEIGHT / 2;
        Hct hctColor = ModMenuSettings.getInstance().getHctColorSetting().getHct();
        int argb = hctColor.toInt();

        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        int backgroundAlpha = Math.round(120 * Math.max(0, Math.min(255, alpha)) / 255F);
        Skia.drawRoundedRect(x, y, width, ROW_HEIGHT, radius, new Color(r, g, b, backgroundAlpha));
    }

    private Color getContentColor(int alpha) {
        Color color = backgroundSetting.isEnabled()
            ? PupperClient.getInstance().getColorManager().getPalette().getOnPrimary()
            : getDesign().getTextColor();
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private List<ModDisplayInfo> getEnabledMods() {
        List<ModDisplayInfo> enabledMods = new ArrayList<>();

        for (Mod mod : PupperClient.getInstance().getModManager().getMods()) {
            if (shouldDisplayMod(mod) && mod.isEnabled() && !mod.isHidden()) {
                String localizedName = mod.getName();
                String displayName = localizedName == null || localizedName.equals("null")
                    ? mod.getRawName()
                    : localizedName;

                // Calculate widths for layout
                float iconWidth = Skia.getTextBounds(Icon.CHECK, Fonts.getIcon(ICON_SIZE)).getWidth();
                float textWidth = Skia.getTextBounds(displayName, Fonts.getRegular(FONT_SIZE)).getWidth();

                // Calculate background widths
                float totalWidth = iconWidth + ICON_TEXT_SPACING + textWidth + HORIZONTAL_PADDING * 2;

                enabledMods.add(new ModDisplayInfo(mod.getRawName(), displayName, totalWidth, iconWidth));
            }
        }

        return enabledMods;
    }

    private boolean shouldDisplayMod(Mod mod) {
        ModCategory category = mod.getCategory();

        return switch (category) {
            case HUD -> hudSetting.isEnabled();
            case RENDER -> renderSetting.isEnabled();
            case PLAYER -> playerSetting.isEnabled();
            case MISC -> otherSetting.isEnabled();
            default -> true;
        };
    }

    private float calculateMaxWidth() {
        float maxWidth = 0;

        // Calculate title width
        String titleIcon = Icon.CODE;
        String titleText = "Active Modules";
        float titleIconWidth = Skia.getTextBounds(titleIcon, Fonts.getIcon(9.7F)).getWidth();
        float titleTextWidth = Skia.getTextBounds(titleText, Fonts.getRegular(FONT_SIZE)).getWidth();
        float titleTotalWidth = titleIconWidth + ICON_TEXT_SPACING + titleTextWidth + HORIZONTAL_PADDING * 2;
        maxWidth = Math.max(maxWidth, titleTotalWidth);

        // Calculate max width from enabled mods
        for (ModDisplayInfo modInfo : sortedDisplayMods) {
            if (modInfo.totalWidth > maxWidth) {
                maxWidth = modInfo.totalWidth;
            }
        }
        return maxWidth;
    }

    private boolean isRightAligned() {
        return "setting.right".equals(modeSetting.getOption());
    }

    private float getAnimationProgress(long currentTime, long startTime, long duration) {
        long elapsed = currentTime - startTime;
        return Math.min(elapsed / (float) duration, 1.0f);
    }

    // Easing functions
    private float easeOutCubic(float x) {
        return (float) (1 - Math.pow(1 - x, 3));
    }

    @Override
    public float getRadius() {
        return 6;
    }

    private static class ModDisplayInfo {
        String originalName;
        String displayName;
        float totalWidth;
        float iconWidth;

        ModDisplayInfo(String originalName, String displayName, float totalWidth, float iconWidth) {
            this.originalName = originalName;
            this.displayName = displayName;
            this.totalWidth = totalWidth;
            this.iconWidth = iconWidth;
        }
    }

    private static class ModAnimationState {
        ModDisplayInfo modInfo;
        long animationStartTime;
        AnimationType animationType;

        ModAnimationState(ModDisplayInfo modInfo, long animationStartTime, AnimationType animationType) {
            this.modInfo = modInfo;
            this.animationStartTime = animationStartTime;
            this.animationType = animationType;
        }
    }

    private enum AnimationType {
        ENTER,
        EXIT
    }
}
