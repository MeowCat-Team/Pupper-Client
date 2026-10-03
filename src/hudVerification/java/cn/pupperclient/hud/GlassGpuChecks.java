package cn.pupperclient.hud;

import cn.pupperclient.ui.render.BlazeUiRenderer;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.*;
import java.util.Arrays;

/** Glass regression checks through the public Blaze3D renderer, including alpha-zero scene RGB. */
public final class GlassGpuChecks {
    public static void main(String[] args) {
        int frames = 0;
        try (var fixture = new UiGpuFixture(); var renderer = new BlazeUiRenderer(); var target = new UiGpuFixture.Target(128, 128)) {
            for (float alpha : new float[]{0, 1}) for (float[] rgb : new float[][]{{.2f, .4f, .6f}, {.65f, .3f, .15f}}) {
                target.clear(rgb[0], rgb[1], rgb[2], alpha);
                byte[] before = target.read();
                renderer.draw(target.view, canvas -> canvas.drawGlass(24, 24, 80, 80, 12, 5, 1.75f), true);
                byte[] after = target.read();
                for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                    for (int channel = 0; channel < 3; channel++) require(Math.abs(target.channel(before, x, y, channel) - target.channel(after, x, y, channel)) <= 2,
                            "Glass changed constant scene RGB at " + x + "," + y);
                    if (x < 23 || x > 104 || y < 23 || y > 104) require(target.channel(after, x, y, 3) == target.channel(before, x, y, 3), "Glass affected exterior alpha");
                }
                require(target.channel(after, 64, 64, 3) == 255, "Glass shader did not run"); frames++;
            }
            target.clear(.2f, .4f, .6f, 0);
            renderer.draw(target.view, canvas -> {
                try (var paint = new Paint().setColor(0xFFFF00FF)) { canvas.drawRect(Rect.makeXYWH(30, 30, 50, 50), paint); }
                canvas.drawGlass(24, 24, 80, 80, 12, 0, 0);
            }, true);
            target.pixel(50, 50, 51, 102, 153, 255, "Scene capture excludes earlier UI"); frames++;

            target.clear(.2f, .4f, .6f, 0);
            renderer.draw(target.view, canvas -> {
                canvas.translate(20, 12); canvas.scale(1.5f, 1.5f);
                try (var clip = Path.makeRRect(RRect.makeXYWH(8, 8, 40, 40, 8)); var opacity = new Paint().setAlpha(128)) {
                    canvas.clipPath(clip, ClipMode.INTERSECT, true);
                    int saved = canvas.saveLayer(Rect.makeXYWH(0, 0, 60, 60), opacity);
                    canvas.drawGlass(0, 0, 60, 60, 8, 3, 1);
                    canvas.restoreToCount(saved);
                }
            }, true);
            target.pixel(60, 50, 51, 102, 153, 128, "Glass opacity group under transformed clip");
            target.pixel(33, 25, 51, 102, 153, 0, "Rounded clip corner");
            target.pixel(12, 12, 51, 102, 153, 0, "Unchanged exterior"); frames++;

            // Use asymmetric scene data to verify scene UVs stay in full-frame coordinates
            // when a glass panel is rendered into a smaller, translated opacity texture.
            target.clear(0, 0, 0, 1);
            renderer.draw(target.view, canvas -> canvas.drawGradient(RRect.makeXYWH(0, 0, 128, 128, 0),
                    new Point(0, 0), new Point(128, 128), new int[]{0xFFFF0000, 0xFF0000FF}, new float[]{0, 1}, 0), false);
            byte[] pattern = target.read();
            renderer.draw(target.view, canvas -> {
                canvas.translate(16, 24); canvas.scale(1.25f, 1.25f);
                try (var opacity = new Paint().setAlpha(128)) {
                    int saved = canvas.saveLayer(Rect.makeXYWH(0, 0, 64, 64), opacity);
                    canvas.drawGlass(0, 0, 64, 64, 8, 0, 0);
                    canvas.restoreToCount(saved);
                }
            }, true);
            byte[] sampled = target.read();
            for (int y = 40; y < 88; y++) for (int x = 32; x < 80; x++) for (int c = 0; c < 4; c++)
                require(Math.abs(target.channel(pattern, x, y, c) - target.channel(sampled, x, y, c)) <= 2, "Glass sampled wrong frame coordinate"); frames++;
            target.clear(.2f, .4f, .6f, 0); byte[] disabled = target.read();
            renderer.draw(target.view, canvas -> canvas.drawGlass(0, 0, 128, 128, 0, 5, 2), false);
            require(Arrays.equals(disabled, target.read()), "Disabled capture drew glass"); frames++;
            System.out.println("Blaze3D glass GPU checks passed: " + frames + " frames; scene-only RGB, alpha zero/one, transformed clips, opacity layers and scene coordinates.");
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
