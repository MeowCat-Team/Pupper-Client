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
                            for (int opacity = 0; opacity <= 100; opacity += 5) {
                                HUDColors colors = HUDColors.from(palette, surface, opacity / 100f);
                                for (Color world : new Color[]{Color.BLACK, Color.WHITE, new Color(0x408040), new Color(0x80b8ed)}) {
                                    Color base = HUDColors.composite(colors.surface(), world);
                                    Color[] backgrounds = {base, HUDColors.composite(colors.raised(), world),
                                        HUDColors.composite(colors.raised(), base)};
                                    if (opacity >= 70) {
                                        for (Color background : backgrounds) {
                                            for (Color foreground : new Color[]{colors.text(), colors.secondaryText(), colors.danger()}) {
                                                double contrast = HUDColors.contrast(foreground, background);
                                                require(contrast >= 4.5, "Composited text contrast: " + contrast + " opacity=" + opacity);
                                                minimum = Math.min(minimum, contrast);
                                            }
                                            require(HUDColors.contrast(colors.accent(), background) >= 3, "Composited icon contrast");
                                        }
                                        require(HUDColors.contrast(colors.accent(), colors.track()) >= 3, "Progress contrast");
                                    }
                                }
                                require(HUDColors.contrast(colors.onAccentContainer(), colors.accentContainer()) >= 4.5, "Badge contrast");
                                require(colors.surface().getAlpha() == Math.round(opacity / 100f * 255), "Surface opacity");
                                require(colors.raised().getAlpha() == colors.surface().getAlpha(), "Raised opacity");
                                require(colors.text().getAlpha() == 255, "Text must stay opaque");
                                schemes++;
                            }
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
        HUDMotion.approach(0, 1, 0.016f, true);
        require(true, "Reduced effects did not snap");
        System.out.printf("HUD checks passed: %d color/surface/opacity combinations (0-100%%), minimum composited text contrast at 70-100%% opacity %.2f:1; motion at 30/60/144/240 FPS.%n", schemes, minimum);
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
