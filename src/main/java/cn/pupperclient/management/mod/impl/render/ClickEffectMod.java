package cn.pupperclient.management.mod.impl.render;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.MouseClickEvent;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.management.mod.settings.impl.ColorSetting;
import cn.pupperclient.management.mod.settings.impl.NumberSetting;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.render.RippleEffect;
import com.mojang.blaze3d.platform.Window;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.Point;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;

public class ClickEffectMod extends Mod {

    private final List<RippleEffect> ripples = new ArrayList<>();
    private int windowWidth, windowHeight;

    // 设置项
    private final ColorSetting colorSetting = new ColorSetting(
        "setting.clickeffect.color",
        "setting.clickeffect.color.description",
        Icon.TOUCHPAD_MOUSE,
        this,
        new Color(255, 255, 255, 180),
        true
    );

    private final NumberSetting maxRadiusSetting = new NumberSetting(
        "setting.clickeffect.radius",
        "setting.clickeffect.radius.description",
        Icon.CIRCLE,
        this,
        5, 5, 20, 1 // 默认10，范围5-20
    );

    private final NumberSetting durationSetting = new NumberSetting(
        "setting.clickeffect.duration",
        "setting.clickeffect.duration.description",
        Icon.TIMER,
        this,
        400, 200, 800, 50 // 默认400ms
    );

    private final BooleanSetting menuOnlySetting = new BooleanSetting(
        "setting.clickeffect.menuonly",
        "setting.clickeffect.menuonly.description",
        Icon.MONITOR,
        this,
        true
    );

    public ClickEffectMod() {
        super("mod.clickeffect.name", "mod.clickeffect.description", Icon.TOUCHPAD_MOUSE, ModCategory.RENDER);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        // 注册事件监听器
        EventBus.getInstance().register(this);
        Window window = Minecraft.getInstance().getWindow();
        windowWidth = window.getGuiScaledWidth();
        windowHeight = window.getGuiScaledHeight();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        EventBus.getInstance().unregister(this);
        ripples.clear();
    }

    public final EventBus.EventListener<MouseClickEvent> onMouseClick = event -> {
        if ((client.gui.screen() instanceof TitleScreen
            || client.gui.screen() instanceof JoinMultiplayerScreen)
            && (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
            || event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        ) {
            ripples.add(new RippleEffect(
                (float) event.getMouseX(),
                (float) event.getMouseY(),
                maxRadiusSetting.getValue(),
                (long) durationSetting.getValue()
            ));
        }
    };

    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
        Window window = Minecraft.getInstance().getWindow();
        windowWidth = window.getGuiScaledWidth();
        windowHeight = window.getGuiScaledHeight();

        Iterator<RippleEffect> iterator = ripples.iterator();
        while (iterator.hasNext()) {
            RippleEffect ripple = iterator.next();
            if (ripple.isAlive()) {
                ripple.update();
                renderRipple(event.getCanvas(), ripple, colorSetting.getColor());
            } else {
                iterator.remove();
            }
        }
    };

    private void renderRipple(Canvas canvas, RippleEffect ripple, Color baseColor) {
        Window window = Minecraft.getInstance().getWindow();
        float windowHeight = window.getGuiScaledHeight();

        float flippedY = windowHeight - ripple.getY();

        float radius = ripple.getRadius();
        float innerAlpha = ripple.getInnerAlpha();
        float outerAlpha = ripple.getOuterAlpha();

        int innerSkColor = io.github.humbleui.skija.Color.makeARGB(
            (int) (innerAlpha * baseColor.getAlpha()),
            baseColor.getRed(),
            baseColor.getGreen(),
            baseColor.getBlue()
        );

        int outerSkColor = io.github.humbleui.skija.Color.makeARGB(
            (int) (outerAlpha * baseColor.getAlpha()),
            baseColor.getRed(),
            baseColor.getGreen(),
            baseColor.getBlue()
        );

        Point center = new Point(ripple.getX(), flippedY);
        try (Shader shader = Shader.makeRadialGradient(
                 center, radius, new int[] { innerSkColor, outerSkColor }, new float[] { 0.0f, 1.0f });
             Paint paint = new Paint()) {
            paint.setShader(shader);
            paint.setAntiAlias(true);
            canvas.drawCircle(ripple.getX(), flippedY, radius, paint);
        }
    }
}
