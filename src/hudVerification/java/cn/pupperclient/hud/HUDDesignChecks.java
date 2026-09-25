package cn.pupperclient.hud;

import java.awt.Color;
import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.mod.api.hud.design.HUDColors;
import cn.pupperclient.management.mod.api.hud.design.HUDMotion;

/** Run with gradlew verifyHudDesign; no game window or third-party test runner needed. */
public final class HUDDesignChecks {
    public static void main(String[] args) {
        int schemes = 0;
        double minimum = Double.MAX_VALUE;
        for (int hue = 0; hue < 360; hue += 30) {
            for (int chroma : new int[]{0, 26, 80}) {
                for (int tone : new int[]{6, 50, 95}) {
                    for (boolean dark : new boolean[]{false, true}) {
                        ColorPalette palette = new ColorPalette(Hct.from(hue, chroma, tone), dark);
                        for (Color surface : new Color[]{palette.getSurface(), palette.getSurfaceContainerLow(), palette.getSurfaceContainer()}) {
                            HUDColors colors = HUDColors.from(palette, surface);
                            for (Color background : new Color[]{colors.surface(), colors.raised()}) {
                                for (Color foreground : new Color[]{colors.text(), colors.secondaryText(), colors.danger()}) {
                                    double contrast = HUDColors.contrast(foreground, background);
                                    require(contrast >= 4.5, "Text contrast: " + contrast);
                                    minimum = Math.min(minimum, contrast);
                                }
                                require(HUDColors.contrast(colors.accent(), background) >= 3, "Icon contrast");
                            }
                            require(HUDColors.contrast(colors.onAccentContainer(), colors.accentContainer()) >= 4.5, "Badge contrast");
                            require(HUDColors.contrast(colors.accent(), colors.track()) >= 3, "Progress contrast");
                            require(colors.surface().getAlpha() == 255 && colors.raised().getAlpha() == 255, "Opaque surfaces");
                            schemes++;
                        }
                    }
                }
            }
        }
        float reference = springAt(60);
        for (int fps : new int[]{30, 60, 144, 240}) {
            require(Math.abs(springAt(fps) - reference) < 0.002f, "Frame rate dependence at " + fps);
            float opacity = 0;
            for (int frame = 0; frame < fps; frame++) opacity = HUDMotion.approach(opacity, 1, 1f / fps, false);
            require(Math.abs(opacity - 1) < 0.01f, "Effect did not settle");
        }
        HUDMotion.Spring spring = new HUDMotion.Spring(0);
        float before = spring.update(1, 0.05f, false);
        float after = spring.update(0, 0.001f, false);
        require(Math.abs(before - after) < 0.02f, "Interrupted spring jumped");
        require(spring.update(12, 0.016f, true) == 12, "Reduced motion did not snap");
        require(HUDMotion.approach(0, 1, 0.016f, true) == 1, "Reduced effects did not snap");
        System.out.printf("HUD checks passed: %d color/surface combinations, minimum text contrast %.2f:1; motion at 30/60/144/240 FPS.%n", schemes, minimum);
    }

    private static float springAt(int fps) {
        HUDMotion.Spring spring = new HUDMotion.Spring(0);
        float value = 0;
        for (int i = 0; i < fps; i++) value = spring.update(i < fps / 2 ? 100 : 20, 1f / fps, false);
        return value;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
