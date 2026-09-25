package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.EventListener;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.event.mod.AutoAgainEvent;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.event.mod.ModStateChangeEvent;
import cn.pupperclient.management.mod.settings.impl.StringSetting;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.color.ColorUtils;
import cn.pupperclient.utils.minecraft.interfaces.IMinecraft;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.types.Rect;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

public class DynamicIsland extends HUDMod implements IMinecraft {
    private final StringSetting name = new StringSetting("mod.DynamicIsland.customname", "mod.DynamicIsland.customname.description", Icon.FLIGHT_LAND, this, "Pupper beta");

    private static final long DISPLAY_DURATION = 2000;
    private static final long TRANSITION_DURATION = 400;
    private static final long NORMAL_TEXT_DURATION = 5000;
    private static final long SMTC_TEXT_DURATION = 3000;
    private static final long AUTO_AGAIN_DISPLAY_TIME = 3000;

    private static boolean isConfigLoading = false;
    private static boolean isAutoAgain = false;
    private static long autoAgainStartTime = 0;

    // Mod状态队列
    private final List<ModStateDisplay> activeModStates = new ArrayList<>();
    private final Queue<ModStateChangeEvent> modStateQueue = new LinkedList<>();

    // 文本循环
    private long lastTextSwitchTime = System.currentTimeMillis();
    private boolean showingSmtcText = false;

    public DynamicIsland() {
        super("mod.DynamicIsland.name", "mod.DynamicIsland.description", Icon.FLIGHT_LAND);
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    public static void setConfigLoading(boolean loading) {
        isConfigLoading = loading;
    }

    public EventBus.EventListener<RenderSkiaEvent> onRenderSkia = this::draw;

    @Override
    public String getIcon() {
        return "";
    }

    @EventListener
    public void onHeypixelAgain(AutoAgainEvent e) {
        isAutoAgain = true;
        autoAgainStartTime = System.currentTimeMillis();
    }

    protected void draw(RenderSkiaEvent event) {
        updateAnimationState();
        updateTextCycle();
        updateAutoAgainState();

        float fontSize = 9;
        float iconSize = 10.5F;
        float padding = 5;
        boolean hasIcon = getIcon() != null && !getIcon().isBlank();

        // 基础高度
        float baseHeight = fontSize + (padding * 2);
        // 扩展高度（用于ModStateText）- 与原来一致
        float expandedHeight = baseHeight * 2;

        // 计算总高度
        float totalHeight = baseHeight;
        if (!activeModStates.isEmpty()) {
            totalHeight = baseHeight + (activeModStates.size() * (expandedHeight + 2)); // 每个ModState之间间隔2像素
        }

        // 计算文本宽度 - 使用normal的宽度计算
        float textWidth = calculateNormalTextWidth(fontSize, iconSize);
        Rect iconBounds = Skia.getTextBounds(getIcon(), Fonts.getIcon(iconSize));
        float width = textWidth + (padding * 2) + (hasIcon ? iconBounds.getWidth() + 4 : 0);

        FontMetrics metrics = Fonts.getGoogleSansRegular(fontSize).getMetrics();
        float textCenterY = (metrics.getAscent() - metrics.getDescent()) / 2 - metrics.getAscent();

        this.begin();

        // 绘制背景 - 使用总高度
        this.drawBackground(getX(), getY(), width, totalHeight);

        if (hasIcon) {
            this.drawText(getIcon(), getX() + padding, getY() + (baseHeight / 2) - (iconBounds.getHeight() / 2),
                Fonts.getIcon(iconSize + 5F));
        }

        float textX = getX() + padding + (hasIcon ? iconBounds.getWidth() + 4 : 0);

        if (!activeModStates.isEmpty()) {
            // 正常文本位置
            float normalTextY = getY() + (baseHeight / 2) - textCenterY;

            // 绘制正常文本
            drawNormalTextWithIcons(textX, normalTextY, fontSize, iconSize);

            // 显示ModStateText - 使用原有的绘制逻辑，只是位置向下堆叠
            for (int i = 0; i < activeModStates.size(); i++) {
                ModStateDisplay state = activeModStates.get(i);
                if (state.alpha > 0.01f) {
                    // 计算每个ModState的Y位置 - 从正常文本下方开始
                    float stateY = getY() + baseHeight + 2 + (i * (expandedHeight + 2));

                    // 为每个ModState绘制独立的背景
                    Skia.drawRoundedRect(getX(), stateY, width, expandedHeight, getRadius(),
                        new Color(30, 30, 30, (int)(200 * state.alpha)));

                    // 计算ModState文本位置 - 在独立背景中居中
                    float stateTextY = stateY + (expandedHeight / 2) - textCenterY;

                    // 使用原有的ModState绘制逻辑
                    drawModStateText(textX, stateTextY, fontSize, iconSize, state);
                }
            }
        } else if (isAutoAgain) {
            // 绘制AutoAgain内容
            float normalTextY = getY() + (baseHeight / 2) - textCenterY;
            drawAutoAgainContent(textX, normalTextY, fontSize, iconSize);
        } else {
            float normalTextY = getY() + (baseHeight / 2) - textCenterY;
            drawNormalTextWithIcons(textX, normalTextY, fontSize, iconSize);
        }

        this.finish();
        position.setSize(width, totalHeight);
    }

    private void drawAutoAgainContent(float textX, float normalTextY, float fontSize, float iconSize) {
        // 计算AutoAgain的透明度
        float currentX = textX;
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - autoAgainStartTime;
        float alpha = 1.0f;

        if (elapsed > AUTO_AGAIN_DISPLAY_TIME - 500) {
            alpha = 1.0f - (float)(elapsed - (AUTO_AGAIN_DISPLAY_TIME - 500)) / 500f;
        }

        alpha = Math.max(0, Math.min(1, alpha));

        // 绘制大号的check图标
        float largeIconSize = iconSize * 2.5f;
        String checkIcon = Icon.CHECK;

        Rect iconBounds = Skia.getTextBounds(checkIcon, Fonts.getIcon(largeIconSize));
        drawTextWithAlpha(checkIcon, currentX, normalTextY, Fonts.getIcon(largeIconSize), alpha);
        currentX += iconBounds.getWidth() + 2;

        String autoAgainText = "Send you next game";
        drawTextWithAlpha(autoAgainText, currentX, normalTextY, Fonts.getGoogleSansRegular(fontSize + 2), alpha);
    }

    private void drawModStateText(float textX, float textY, float fontSize, float iconSize, ModStateDisplay state) {
        if (state.event == null) return;

        // 绘制标题
        drawTextWithAlpha("Module Toggle", textX, textY - fontSize - 2,
            Fonts.getGoogleSansRegular(fontSize), state.alpha);

        // 绘制状态图标和文本
        String stateIcon = state.event.isEnabled() ? Icon.CHECK : Icon.CLOSE;
        drawTextWithAlpha(stateIcon, textX, textY + 4, Fonts.getIcon(iconSize), state.alpha);

        String modStateText = getModStateText(state.event);
        drawTextWithAlpha(modStateText, textX + Skia.getTextBounds(stateIcon, Fonts.getIcon(iconSize)).getWidth() + 2,
            textY + 4, Fonts.getGoogleSansRegular(fontSize), state.alpha);
    }

    private void drawNormalTextWithIcons(float startX, float startY, float fontSize, float iconSize) {
        float currentX = startX;

        Skia.drawImage("logo.png", currentX - 6, startY - 4.5F, iconSize + 6, iconSize + 6);
        currentX += iconSize + 2;

        drawTextWithAlpha(name.getValue() + " · ", currentX, startY, Fonts.getGoogleSansRegular(fontSize), (float) 1.0);
        currentX += Skia.getTextBounds(name.getValue() + " · ", Fonts.getGoogleSansRegular(fontSize)).getWidth() + 5;

        // 玩家图标和名称
        String playerIcon = Icon.PERSON;
        drawTextWithAlpha(playerIcon, currentX, startY, Fonts.getIcon(iconSize), (float) 1.0);
        currentX += Skia.getTextBounds(playerIcon, Fonts.getIcon(iconSize)).getWidth() + 2;

        String playerName = client.player != null ? client.player.getName().getString() : "NULL";
        drawTextWithAlpha(playerName + " · ", currentX, startY, Fonts.getGoogleSansRegular(fontSize), (float) 1.0);
        currentX += Skia.getTextBounds(playerName + " · ", Fonts.getGoogleSansRegular(fontSize)).getWidth() + 5;

        // 服务器信息和延迟
        String linkIcon = Icon.LINK;
        drawTextWithAlpha(linkIcon, currentX, startY + 1, Fonts.getIcon(iconSize), (float) 1.0);
        currentX += Skia.getTextBounds(linkIcon, Fonts.getIcon(iconSize)).getWidth() + 2;

        String serverInfo = getServerInfo();
        drawTextWithAlpha(serverInfo + " · ", currentX, startY, Fonts.getGoogleSansRegular(fontSize), (float) 1.0);
        currentX += Skia.getTextBounds(serverInfo + " · ", Fonts.getGoogleSansRegular(fontSize)).getWidth() + 5;

        // FPS图标和数值
        String fpsIcon = Icon.DESKTOP_WINDOWS;
        drawTextWithAlpha(fpsIcon, currentX, startY - 1F, Fonts.getIcon(iconSize), (float) 1.0);
        currentX += Skia.getTextBounds(fpsIcon, Fonts.getIcon(iconSize)).getWidth() + 2;

        String fpsText = client.getFps() + " FPS";
        drawTextWithAlpha(fpsText, currentX, startY, Fonts.getGoogleSansRegular(fontSize), (float) 1.0);
    }

    private float calculateNormalTextWidth(float fontSize, float iconSize) {
        float totalWidth = 0;

        totalWidth += iconSize + 2;
        totalWidth += Skia.getTextBounds(name.getValue() + " · ", Fonts.getGoogleSansRegular(fontSize)).getWidth() + 5;

        // 玩家图标和名称
        String playerIcon = Icon.PERSON;
        totalWidth += Skia.getTextBounds(playerIcon, Fonts.getIcon(iconSize)).getWidth() + 2;
        String playerName = client.player != null ? client.player.getName().getString() : "NULL";
        totalWidth += Skia.getTextBounds(playerName + " · ", Fonts.getGoogleSansRegular(fontSize)).getWidth() + 5;

        // 服务器信息和延迟
        String linkIcon = Icon.LINK;
        totalWidth += Skia.getTextBounds(linkIcon, Fonts.getIcon(iconSize)).getWidth() + 2;
        String serverInfo = getServerInfo();
        totalWidth += Skia.getTextBounds(serverInfo + " · ", Fonts.getGoogleSansRegular(fontSize)).getWidth() + 5;

        // FPS图标和数值
        String fpsIcon = Icon.DESKTOP_WINDOWS;
        totalWidth += Skia.getTextBounds(fpsIcon, Fonts.getIcon(iconSize)).getWidth() + 2;
        String fpsText = client.getFps() + " FPS";
        totalWidth += Skia.getTextBounds(fpsText, Fonts.getGoogleSansRegular(fontSize)).getWidth() + 5;

        return totalWidth;
    }

    private void updateAnimationState() {
        long currentTime = System.currentTimeMillis();

        // 处理队列中的新事件
        while (!modStateQueue.isEmpty() && activeModStates.size() < 5) {
            ModStateChangeEvent newEvent = modStateQueue.poll();
            activeModStates.add(new ModStateDisplay(newEvent, currentTime));
        }

        // 更新所有活跃状态的动画
        Iterator<ModStateDisplay> iterator = activeModStates.iterator();
        while (iterator.hasNext()) {
            ModStateDisplay state = iterator.next();
            long elapsed = currentTime - state.startTime;

            if (elapsed < TRANSITION_DURATION) {
                // 进入动画
                float progress = elapsed / (float) TRANSITION_DURATION;
                state.alpha = easeOutCubic(progress);
            } else if (elapsed < DISPLAY_DURATION - TRANSITION_DURATION) {
                // 完全显示
                state.alpha = 1.0f;
            } else if (elapsed < DISPLAY_DURATION) {
                // 退出动画
                float progress = (elapsed - (DISPLAY_DURATION - TRANSITION_DURATION)) / (float) TRANSITION_DURATION;
                state.alpha = 1.0f - easeInCubic(progress);
            } else {
                // 动画结束，移除状态
                iterator.remove();
            }
        }
    }

    private void updateAutoAgainState() {
        if (isAutoAgain) {
            long currentTime = System.currentTimeMillis();
            long elapsed = currentTime - autoAgainStartTime;

            if (elapsed >= AUTO_AGAIN_DISPLAY_TIME) {
                isAutoAgain = false;
                autoAgainStartTime = 0;
            }
        }
    }

    private void updateTextCycle() {
        if (!activeModStates.isEmpty() || isAutoAgain) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastTextSwitchTime;

        if (showingSmtcText) {
            if (elapsed >= SMTC_TEXT_DURATION) {
                showingSmtcText = false;
                lastTextSwitchTime = currentTime;
            }
        } else {
            if (elapsed >= NORMAL_TEXT_DURATION) {
                showingSmtcText = true;
                lastTextSwitchTime = currentTime;
            }
        }
    }

    private float easeOutCubic(float x) {
        return (float) (1 - Math.pow(1 - x, 3));
    }

    private float easeInCubic(float x) {
        return x * x * x;
    }

    private @NotNull String getServerInfo() {
        if (client.getCurrentServer() != null && client.player != null) {
            String serverAddress = client.getCurrentServer().ip;
            long ping = client.getCurrentServer().ping;
            return ping + "ms to " + serverAddress;
        }
        return "Singleplayer";
    }

    private @NotNull String getModStateText(ModStateChangeEvent event) {
        if (event == null) return "";

        String modName;
        if(event.getMod().getName().equals("null") || event.getMod().getName() == null) modName = event.getMod().getRawName();
        else modName = event.getMod().getName();
        String state = event.getMod().isEnabled() ? "enabled" : "disabled";

        return modName + " has been " + state;
    }

    private void drawTextWithAlpha(String text, float x, float y, io.github.humbleui.skija.Font font, float alpha) {
		Color color = ColorUtils.applyAlpha(getDesign().getTextColor(), alpha);
        Skia.drawText(text, x, y, color, font);
    }

    @EventListener
    private void handleModStateChange(ModStateChangeEvent event) {
        if (isConfigLoading) {
            return;
        }

        if (Objects.equals(event.getMod().getRawName(), getRawName())) {
            return;
        }

        modStateQueue.offer(event);
    }

    @Override
    public float getRadius() {
        return 6;
    }

    private static class ModStateDisplay {
        final ModStateChangeEvent event;
        final long startTime;
        float alpha = 0.0f;

        ModStateDisplay(ModStateChangeEvent event, long startTime) {
            this.event = event;
            this.startTime = startTime;
        }
    }
}
