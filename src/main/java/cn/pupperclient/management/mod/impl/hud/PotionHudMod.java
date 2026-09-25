package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.PupperClient;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.management.mod.settings.impl.ComboSetting;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.misc.RomanConverter;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

@SuppressWarnings("unused")
public class PotionHudMod extends HUDMod {
    private static PotionHudMod instance;

    // Settings
    private final BooleanSetting backgroundSetting = new BooleanSetting("setting.background",
        "setting.background1.description", Icon.IMAGE, this, true);
    private final ComboSetting modeSetting = new ComboSetting("setting.mode",
        "setting.mode.description", Icon.ALIGN_HORIZONTAL_RIGHT, this,
        Arrays.asList("setting.right", "setting.left"), "setting.right");

    // Design constants
    private static final float FONT_SIZE = 8.5f;
    private static final float ROW_HEIGHT = 14f;
    private static final float HORIZONTAL_PADDING = 7f;
    private static final float VERTICAL_PADDING = 3f;
    private static final float ITEM_SPACING = 2f;
    private static final float ICON_TEXT_SPACING = 4f;
    private static final float EFFECT_TIME_SPACING = 8f;
    private static final long ANIMATION_DURATION = 300L;

    // Animation states
    private final Map<String, PotionAnimationState> animationStates = new ConcurrentHashMap<>();
    private final List<PotionDisplayInfo> sortedDisplayPotions = new ArrayList<>();

    public PotionHudMod() {
        super("mod.potionhud.name", "mod.potionhud.description", Icon.LIST);
        instance = this;
    }

    public static PotionHudMod getInstance() {
        return instance;
    }

    private final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> draw();

    private void draw() {
        try {
            begin();
            updateAnimationStates();
            drawPotionHud();
        } catch (Exception e) {
            PupperClient.LOGGER.error("Error drawing PotionHudMod", e);
            position.setSize(100, 20);
        } finally {
            finish();
        }
    }

    private void updateAnimationStates() {
        long currentTime = System.currentTimeMillis();

        // Get current active potion effects
        List<PotionDisplayInfo> activePotions = getActivePotions();

        // Update animation states for active potions
        for (PotionDisplayInfo potionInfo : activePotions) {
            PotionAnimationState state = animationStates.get(potionInfo.effectId);
            if (state == null) {
                // New effect - start enter animation
                state = new PotionAnimationState(potionInfo, currentTime, AnimationType.ENTER);
                animationStates.put(potionInfo.effectId, state);
            } else if (state.animationType == AnimationType.EXIT) {
                // Effect was exiting but got re-applied - switch to enter animation
                state.animationType = AnimationType.ENTER;
                state.animationStartTime = currentTime;
                state.potionInfo = potionInfo;
            } else {
                // Update existing effect info
                state.potionInfo = potionInfo;
            }
        }

        // Handle effects that are no longer active
        Iterator<Map.Entry<String, PotionAnimationState>> iterator = animationStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PotionAnimationState> entry = iterator.next();
            String effectId = entry.getKey();
            PotionAnimationState state = entry.getValue();

            boolean stillActive = activePotions.stream()
                .anyMatch(potion -> potion.effectId.equals(effectId));

            if (!stillActive && state.animationType != AnimationType.EXIT) {
                // Start exit animation
                state.animationType = AnimationType.EXIT;
                state.animationStartTime = currentTime;
            }

            // Remove effects that have finished exit animation
            if (state.animationType == AnimationType.EXIT) {
                float progress = getAnimationProgress(currentTime, state.animationStartTime, ANIMATION_DURATION);
                if (progress >= 1.0f) {
                    iterator.remove();
                }
            }
        }

        // Update sorted display potions
        updateSortedDisplayPotions();
    }

    private void updateSortedDisplayPotions() {
        sortedDisplayPotions.clear();

        // Add all active effects (both entering and stable)
        for (PotionAnimationState state : animationStates.values()) {
            if (state.animationType != AnimationType.EXIT ||
                getAnimationProgress(System.currentTimeMillis(), state.animationStartTime, ANIMATION_DURATION) < 1.0f) {
                sortedDisplayPotions.add(state.potionInfo);
            }
        }

        // Sort by display name length (longest first) for consistent layout
        sortedDisplayPotions.sort((a, b) -> Float.compare(b.totalWidth, a.totalWidth));
    }

    private void drawPotionHud() {
        boolean isRightAligned = isRightAligned();

        // Calculate total height including title and all effect entries
        int totalEntries = 1 + sortedDisplayPotions.size(); // 1 for title + effect entries
        float totalHeight = totalEntries * (ROW_HEIGHT + ITEM_SPACING) - ITEM_SPACING;

        // Calculate max width
        float maxWidth = calculateMaxWidth();

        float currentY = 0;

        // Draw title "Active Potions" (always visible)
        drawTitleEntry(maxWidth, currentY, isRightAligned);
        currentY += ROW_HEIGHT + ITEM_SPACING;

        // Draw active potion effects
        for (int i = 0; i < sortedDisplayPotions.size(); i++) {
            PotionDisplayInfo potionInfo = sortedDisplayPotions.get(i);
            PotionAnimationState state = animationStates.get(potionInfo.effectId);

            if (state != null) {
                drawPotionEntry(potionInfo, state, maxWidth, currentY, i, isRightAligned);
            }

            currentY += ROW_HEIGHT + ITEM_SPACING;
        }

        position.setSize(maxWidth, totalHeight);
    }

    private void drawTitleEntry(float maxWidth, float y, boolean isRightAligned) {
        String titleIcon = Icon.SCIENCE;
        String titleText = "Active Potions";

        // Calculate widths
        float iconWidth = Skia.getTextBounds(titleIcon, Fonts.getIcon(9.25F)).getWidth();
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
        Skia.drawText(titleIcon, iconX, contentY, contentColor, Fonts.getIcon(9.25F));
        Skia.drawText(titleText, textX, contentY, contentColor, Fonts.getRegular(FONT_SIZE));
    }

    private void drawPotionEntry(PotionDisplayInfo potionInfo, PotionAnimationState state, float maxWidth,
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
        float totalWidth = potionInfo.totalWidth;
        float bgX = getX() + (isRightAligned ? (maxWidth - totalWidth) : 0) + animationOffset;
        float bgY = getY() + y;

        // Draw backgrounds with alpha
        if (backgroundSetting.isEnabled()) {
            drawRoundedBackground(bgX, bgY, totalWidth, (int) alpha);
        }

        // Draw content with alpha
        Color contentColor = getContentColor((int) alpha);

        // Draw effect name and time
        float effectX = bgX + HORIZONTAL_PADDING;
        float timeX = effectX + potionInfo.effectWidth + EFFECT_TIME_SPACING;
        float contentY = bgY + VERTICAL_PADDING;

        Skia.drawText(potionInfo.effectName, effectX, contentY, contentColor, Fonts.getRegular(FONT_SIZE));
        Skia.drawText(potionInfo.timeText, timeX, contentY, contentColor, Fonts.getRegular(FONT_SIZE));
    }

    private void drawRoundedBackground(float x, float y, float width, int alpha) {
        float radius = ROW_HEIGHT / 2;
        Color color = PupperClient.getInstance().getColorManager().getPalette().getPrimary();
        int backgroundAlpha = Math.round(120 * Math.max(0, Math.min(255, alpha)) / 255F);
        Skia.drawRoundedRect(x, y, width, ROW_HEIGHT, radius,
            new Color(color.getRed(), color.getGreen(), color.getBlue(), backgroundAlpha));
    }

    private Color getContentColor(int alpha) {
        Color color = backgroundSetting.isEnabled()
            ? PupperClient.getInstance().getColorManager().getPalette().getOnPrimary()
            : getDesign().getTextColor();
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private List<PotionDisplayInfo> getActivePotions() {
        List<PotionDisplayInfo> activePotions = new ArrayList<>();

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return activePotions;

        // Get all active status effects
        Collection<MobEffectInstance> effects = client.player.getActiveEffects();

        for (MobEffectInstance effect : effects) {
            Holder <MobEffect> statusEffect = effect.getEffect();
            String effectkey = statusEffect.value().getDescriptionId();
            int level = effect.getAmplifier() + 1;
            String amplifier = level > 1 ? " " + RomanConverter.intToRomanByPlace(level) : "";
            String effectName = statusEffect.value().getDisplayName().getString() + amplifier;
            String timeText = formatDuration(effect);

            // Calculate widths for layout
            float effectWidth = Skia.getTextBounds(effectName, Fonts.getRegular(FONT_SIZE)).getWidth();
            float timeWidth = Skia.getTextBounds(timeText, Fonts.getRegular(FONT_SIZE)).getWidth();

            // Calculate background widths
            float totalWidth = effectWidth + EFFECT_TIME_SPACING + timeWidth + HORIZONTAL_PADDING * 2;

            activePotions.add(new PotionDisplayInfo(effectkey, effectName, timeText,
                totalWidth, effectWidth));
        }

        return activePotions;
    }

    private String formatDuration(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) {
            return "inf";
        }

        int duration = effect.getDuration();
        int seconds = duration / 20;
        int minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private float calculateMaxWidth() {
        float maxWidth = 0;

        // Calculate title width
        String titleText = "Active Potions";
        float titleIconWidth = Skia.getTextBounds(Icon.SCIENCE, Fonts.getIcon(9.25F)).getWidth();
        float titleWidth = Skia.getTextBounds(titleText, Fonts.getRegular(FONT_SIZE)).getWidth();
        float titleTotalWidth = titleIconWidth + ICON_TEXT_SPACING + titleWidth + HORIZONTAL_PADDING * 2;
        maxWidth = Math.max(maxWidth, titleTotalWidth);

        // Calculate max width from active potions
        for (PotionDisplayInfo potionInfo : sortedDisplayPotions) {
            if (potionInfo.totalWidth > maxWidth) {
                maxWidth = potionInfo.totalWidth;
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

    private static class PotionDisplayInfo {
        String effectId;
        String effectName;
        String timeText;
        float totalWidth;
        float effectWidth;

        PotionDisplayInfo(String effectId, String effectName, String timeText,
                          float totalWidth, float effectWidth) {
            this.effectId = effectId;
            this.effectName = effectName;
            this.timeText = timeText;
            this.totalWidth = totalWidth;
            this.effectWidth = effectWidth;
        }
    }

    private static class PotionAnimationState {
        PotionDisplayInfo potionInfo;
        long animationStartTime;
        AnimationType animationType;

        PotionAnimationState(PotionDisplayInfo potionInfo, long animationStartTime, AnimationType animationType) {
            this.potionInfo = potionInfo;
            this.animationStartTime = animationStartTime;
            this.animationType = animationType;
        }
    }

    private enum AnimationType {
        ENTER,
        EXIT
    }
}
