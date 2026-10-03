package cn.pupperclient.hud;

import cn.pupperclient.ui.render.BlazeUiRenderer;
import cn.pupperclient.ui.render.UiCanvas;
import cn.pupperclient.skia.context.SkiaContext;
import cn.pupperclient.skia.font.Fonts;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.*;
import java.util.Arrays;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.PrimitiveTopology;
import net.minecraft.resources.Identifier;
import java.util.Optional;

/** Exercise the production geometry, glyph atlas, clipping and compositor on a real device. */
public final class SkiaInteropGpuChecks {
    private static int scenarios;
    public static void main(String[] args) {
        try (var fixture = new UiGpuFixture(); var renderer = new BlazeUiRenderer(); var target = new UiGpuFixture.Target(128, 96)) {
            for (int frame = 0; frame < 4; frame++) {
                float alpha = frame % 2;
                target.clear(.2f, .4f, .6f, alpha);
                byte[] before = target.read();
                renderer.draw(target.view, canvas -> {
                    require(Arrays.equals(before, target.read()), "Recording altered the destination");
                    rect(canvas, 2, 2, 12, 10, 0xFFFF0000);
                    rect(canvas, 2, 78, 12, 10, 0xFF0000FF);
                    rect(canvas, 24, 16, 16, 16, 0x80FF0000);
                }, false);
                target.pixel(6, 6, 255, 0, 0, 255, "Top marker");
                target.pixel(6, 82, 0, 0, 255, 255, "Bottom marker");
                target.pixel(32, 24, 153, 51, 76, alpha == 0 ? 128 : 255, "Premultiplied blend once");
                target.pixel(120, 88, 51, 102, 153, (int) alpha * 255, "Untouched exterior"); scenarios++;
            }
            target.clear(0, 0, 0, 0);
            renderer.draw(target.view, canvas -> {
                int saved = canvas.save(); canvas.translate(12, 8); canvas.scale(2, 2);
                try (var clip = Path.makeRRect(RRect.makeXYWH(0, 0, 24, 24, 6));
                     var hole = Path.makeRRect(RRect.makeXYWH(8, 8, 8, 8, 2))) {
                    canvas.clipPath(clip, ClipMode.INTERSECT, true);
                    canvas.clipPath(hole, ClipMode.DIFFERENCE, true);
                    rect(canvas, -8, -8, 40, 40, 0xFF00FF00);
                }
                canvas.restoreToCount(saved);
                rect(canvas, 90, 60, 12, 12, 0xFF0000FF);
            }, false);
            target.pixel(14, 10, 0, 0, 0, 0, "Rounded clip corner");
            target.pixel(20, 30, 0, 255, 0, 255, "Transformed intersection");
            target.pixel(36, 32, 0, 0, 0, 0, "Difference clip hole");
            target.pixel(95, 65, 0, 0, 255, 255, "Restored clip and transform"); scenarios++;

            // Subsequent Minecraft-style draws must still see their own texture/sampler bindings.
            try (var base = new UiGpuFixture.Target(2, 2); var lightmap = new UiGpuFixture.Target(2, 2)) {
                var pipeline = RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("pupper", "test/lightmap"))
                        .withVertexShader(Identifier.fromNamespaceAndPath("pupper", "skia_ui"))
                        .withFragmentShader(Identifier.fromNamespaceAndPath("pupper", "test_lightmap"))
                        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                        .withBindGroupLayout(BindGroupLayout.builder().withSampler("Base").withSampler("Lightmap").build())
                        .withColorTargetState(new ColorTargetState(Optional.empty(), com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
                        .withDepthStencilState(Optional.empty()).withCull(false).build();
                base.clear(.8f, .5f, .25f, 1); lightmap.clear(.5f, .8f, .6f, 1);
                for (int frame = 0; frame < 4; frame++) {
                    renderer.draw(target.view, canvas -> rect(canvas, 0, 0, 128, 96, 0xFFFF0000), true);
                    try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Lightmap after UI", target.view, Optional.empty())) {
                        pass.setPipeline(pipeline);
                        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
                        pass.bindTexture("Base", base.view, sampler); pass.bindTexture("Lightmap", lightmap.view, sampler);
                        pass.draw(3, 1, 0, 0);
                    }
                    target.pixel(60, 40, 102, 102, 38, 255, "Lightmap colors after UI");
                }
            }
            scenarios++;

            target.clear(0, 0, 0, 0);
            renderer.draw(target.view, canvas -> {
                try (var ring = new Paint().setColor(0xFF00FF00).setMode(PaintMode.STROKE).setStrokeWidth(3);
                     var arc = new Paint().setColor(0xFFFF0000).setMode(PaintMode.STROKE).setStrokeWidth(2)) {
                    canvas.drawCircle(20, 20, 10, ring);
                    canvas.drawArc(50, 10, 70, 30, 0, 90, false, arc);
                    canvas.drawRadialCircle(100, 20, 10, -1, 0x00FFFFFF);
                }
            }, false);
            target.pixel(20, 20, 0, 0, 0, 0, "Ring center remains empty");
            target.pixel(29, 20, 0, 255, 0, 255, "Ring stroke");
            target.pixel(69, 22, 255, 0, 0, 255, "Arc inside sweep");
            target.pixel(50, 20, 0, 0, 0, 0, "Arc outside sweep");
            byte[] ripple = target.read();
            require(target.channel(ripple, 100, 20, 3) > 230 && target.channel(ripple, 109, 20, 3) < 30, "Radial ripple alpha falloff");
            scenarios++;

            target.clear(0, 0, 0, 0);
            renderer.draw(target.view, canvas -> {
                try (var opacity = new Paint().setAlpha(128)) {
                    int saved = canvas.saveLayer(Rect.makeXYWH(8, 8, 80, 60), opacity);
                    rect(canvas, 10, 10, 36, 36, -1); rect(canvas, 30, 10, 36, 36, -1);
                    canvas.restoreToCount(saved);
                }
                canvas.drawGradient(RRect.makeXYWH(8, 65, 80, 24, 4), new Point(8, 65), new Point(88, 65),
                        new int[]{0xFFFF0000, 0xFF0000FF}, new float[]{0, 1}, 0);
            }, false);
            target.pixel(20, 20, 128, 128, 128, 128, "Group opacity");
            target.pixel(40, 20, 128, 128, 128, 128, "Overlapping group opacity once");
            byte[] gradient = target.read();
            require(target.channel(gradient, 12, 77, 0) > 220 && target.channel(gradient, 82, 77, 2) > 220, "Gradient orientation/colors"); scenarios++;

            try (var source = Surface.makeRasterN32Premul(8, 8); var paint = new Paint().setColor(-1)) {
                source.getCanvas().clear(0xFFFF0000);
                source.getCanvas().drawRect(Rect.makeXYWH(0, 4, 8, 4), paint.setColor(0xFF0000FF));
                try (var image = source.makeImageSnapshot()) {
                    var font = Fonts.getRegular(16);
                    target.clear(0, 0, 0, 0);
                    renderer.draw(target.view, canvas -> {
                        canvas.drawImageRect(image, Rect.makeXYWH(90, 6, 24, 24));
                        canvas.drawString("Pupper 音乐", 8, 50, font, paint.setColor(-1));
                    }, false);
                    target.pixel(100, 10, 255, 0, 0, 255, "CPU image top orientation");
                    target.pixel(100, 27, 0, 0, 255, 255, "CPU image bottom orientation");
                    byte[] first = target.read(); int visible = 0;
                    for (int y = 32; y < 55; y++) for (int x = 8; x < 88; x++) if (target.channel(first, x, y, 3) > 64) visible++;
                    require(visible > 120, "Glyph atlas produced no readable text");
                    int rasterizations = renderer.assetRasterizations();
                    target.clear(0, 0, 0, 0);
                    renderer.draw(target.view, canvas -> {
                        canvas.drawImageRect(image, Rect.makeXYWH(90, 6, 24, 24));
                        canvas.drawString("Pupper 音乐", 8, 50, font, paint.setColor(-1));
                    }, false);
                    require(renderer.assetRasterizations() == rasterizations, "Unchanged text/artwork was rerasterized");
                    require(Arrays.equals(first, target.read()), "Cached frame differs");
                }
                // Retired artwork is closed before the following frame: cache cleanup must not read native dimensions.
                renderer.draw(target.view, canvas -> {}, false);
            }
            scenarios++;
            try (var reference = Surface.makeRasterN32Premul(128, 96); var white = new Paint().setColor(-1)) {
                var testFont = Fonts.getRegular(28);
                reference.getCanvas().clear(0); reference.getCanvas().scale(2, 2);
                reference.getCanvas().drawString("Pupp", 2, 28, testFont, white);
                java.awt.image.BufferedImage expected;
                try (var image = reference.makeImageSnapshot(); var png = image.encodeToData(EncodedImageFormat.PNG)) {
                    expected = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png.getBytes()));
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                target.clear(0,0,0,0);
                renderer.draw(target.view, canvas -> {canvas.scale(2,2);canvas.drawString("Pupp",2,28,testFont,white);}, false);
                byte[] actual = target.read(); int stray = 0;
                for (int y=2;y<94;y++) for(int x=2;x<126;x++) {
                    int nearby=0;
                    for(int dy=-2;dy<=2;dy++) for(int dx=-2;dx<=2;dx++) nearby=Math.max(nearby,expected.getRGB(x+dx,y+dy)>>>24);
                    if(nearby==0 && target.channel(actual,x,y,3)>8) stray++;
                }
                require(stray == 0, "Glyph atlas sampled outside the reference glyphs: " + stray + " stray pixels");
            }
            byte[] before = target.read(); boolean threw = false;
            try { renderer.draw(target.view, canvas -> { rect(canvas, 0, 0, 128, 96, -1); throw new IllegalStateException("fixture"); }, true); }
            catch (IllegalStateException expected) { threw = true; }
            require(threw && Arrays.equals(before, target.read()), "Failed UI frame changed the destination"); scenarios++;
            try (var resized = new UiGpuFixture.Target(192, 128); var borrowed = new UiGpuFixture.Target(8, 8)) {
                borrowed.clear(0, 1, 0, 1); resized.clear(0, 0, 0, 0);
                renderer.draw(resized.view, canvas -> canvas.drawTexture(borrowed.view, Rect.makeWH(8, 8), Rect.makeXYWH(4, 4, 20, 20), 1), false);
                resized.pixel(12, 12, 0, 255, 0, 255, "Borrowed GPU image after resize");
                renderer.close(); require(!borrowed.view.isClosed() && !borrowed.texture.isClosed(), "Renderer closed Minecraft-owned texture");
            }
            scenarios++;
            // Exercise the actual event drawing facade and repeated shutdown.
            target.clear(0, 0, 0, 0);
            SkiaContext.drawOffscreen(target.view, canvas -> cn.pupperclient.skia.Skia.drawRect(4, 4, 12, 12, java.awt.Color.RED), false);
            target.pixel(8, 8, 255, 0, 0, 255, "Production context/facade");
            SkiaContext.close(); SkiaContext.close();
            System.out.println("Blaze3D UI GPU checks passed: " + scenarios + " scenarios; composition, clips, opacity groups, gradients, cached glyphs/images, resize and ownership.");
        }
    }
    private static void rect(UiCanvas canvas, float x, float y, float w, float h, int color) {
        try (var paint = new Paint().setColor(color)) { canvas.drawRect(Rect.makeXYWH(x, y, w, h), paint); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
