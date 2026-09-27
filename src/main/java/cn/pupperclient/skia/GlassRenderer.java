package cn.pupperclient.skia;

import cn.pupperclient.PupperLogger;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.RuntimeEffect;
import io.github.humbleui.skija.RuntimeEffectBuilder;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.RRect;

/**
 * Rounded, scene-only glass backdrops. The caller paints tint, rim and content afterwards.
 * Capturing before any custom HUD drawing prevents text and overlapping panels feeding back
 * into another panel. All samples retain the scene RGB; the backbuffer's alpha is not opacity.
 */
public final class GlassRenderer {
    private static final String EFFECT_SOURCE = """
        uniform shader scene;
        uniform float3 deviceX;
        uniform float3 deviceY;
        uniform float3 deviceW;
        uniform float4 bounds;
        uniform float radius;
        uniform float softness;
        uniform float refraction;

        float2 toDevice(float2 p) {
            float3 point = float3(p, 1.0);
            float w = dot(deviceW, point);
            return float2(dot(deviceX, point), dot(deviceY, point)) / w;
        }

        half4 main(float2 p) {
            float2 center = bounds.xy + bounds.zw * 0.5;
            float2 relative = p - center;
            float2 q = abs(relative) - (bounds.zw * 0.5 - radius);
            float2 outside = max(q, float2(0.0));
            float distance = length(outside) + min(max(q.x, q.y), 0.0) - radius;
            float2 normal;
            if (length(outside) > 0.0001) {
                normal = normalize(outside) * sign(relative);
            } else {
                normal = q.x > q.y ? float2(sign(relative.x), 0.0)
                                   : float2(0.0, sign(relative.y));
            }
            // A small curved edge displaces only the background, never the content above it.
            float edgeWidth = max(2.0, min(radius, 10.0));
            float edge = 1.0 - smoothstep(0.0, edgeWidth, max(-distance, 0.0));
            float2 samplePoint = p - normal * (refraction * edge * edge);

            // A normalized Gaussian stencil cannot add brightness or contrast. Keep the blur
            // restrained so the edge displacement remains visible instead of opaque frosting.
            half3 rgb = half3(0.0);
            float2 devicePoint = toDevice(samplePoint);
            float2 dx = toDevice(samplePoint + float2(softness * 1.4142, 0.0)) - devicePoint;
            float2 dy = toDevice(samplePoint + float2(0.0, softness * 1.4142)) - devicePoint;
            for (int iy = -1; iy <= 1; ++iy) {
                float wy = iy == 0 ? 2.0 : 1.0;
                for (int ix = -1; ix <= 1; ++ix) {
                    float wx = ix == 0 ? 2.0 : 1.0;
                    rgb += scene.eval(devicePoint + dx * float(ix) + dy * float(iy)).rgb * half(wx * wy / 16.0);
                }
            }
            // Minecraft's displayed framebuffer RGB is opaque regardless of its alpha bits.
            // Reusing those alpha bits in SRC_OVER can add the scene to itself (overexposure).
            return half4(rgb, 1.0);
        }
        """;

    private static RuntimeEffect effect;
    private static Image sceneImage;
    private static Shader sceneShader;
    private static boolean unavailable;

    private GlassRenderer() {}

    /** Called exactly once at the start of a Skia frame, before HUD/GUI rendering. */
    public static void beginFrame(Surface surface) {
        endFrame();
        if (unavailable) return;
        try {
            if (effect == null) effect = RuntimeEffect.makeForShader(EFFECT_SOURCE);
            sceneImage = surface.makeImageSnapshot();
            sceneShader = sceneImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP,
                    SamplingMode.LINEAR, null);
        } catch (RuntimeException exception) {
            disable(exception);
        }
    }

    /**
     * Draws the scene inside the current transform and clip, including animated alpha layers.
     * Strength and refraction are local UI pixels; tint opacity is deliberately independent.
     */
    public static void draw(Canvas canvas, float x, float y, float width, float height,
                            float radius, float strength, float refraction) {
        if (sceneShader == null || unavailable || !Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) return;
        float corner = finiteClamp(radius, 0, Math.min(width, height) / 2, 0);
        RRect panel = RRect.makeXYWH(x, y, width, height, corner);
        if (canvas.quickReject(panel)) return;
        float[] transform = canvas.getLocalToDeviceAsMatrix33().getMat();
        for (float value : transform) if (!Float.isFinite(value)) return;
        // Animation may reach a zero scale for one frame; avoid division by a singular matrix.
        float determinant = transform[0] * (transform[4] * transform[8] - transform[5] * transform[7])
                - transform[1] * (transform[3] * transform[8] - transform[5] * transform[6])
                + transform[2] * (transform[3] * transform[7] - transform[4] * transform[6]);
        if (Math.abs(determinant) < 0.000001F) return;
        // This Skija version exposes a separately ref-counted Data wrapper on builders.
        try (RuntimeEffectBuilder builder = new RuntimeEffectBuilder(effect);
             Data uniforms = builder.getUniforms(); Paint paint = new Paint()) {
            builder.setChild("scene", sceneShader);
            builder.setUniform("deviceX", transform[0], transform[1], transform[2]);
            builder.setUniform("deviceY", transform[3], transform[4], transform[5]);
            builder.setUniform("deviceW", transform[6], transform[7], transform[8]);
            builder.setUniform("bounds", x, y, width, height);
            builder.setUniform("radius", corner);
            builder.setUniform("softness", finiteClamp(strength, 0, 8, 2));
            builder.setUniform("refraction", finiteClamp(refraction, 0, 4, 1.5F));
            try (Shader shader = builder.makeShader()) {
                paint.setAntiAlias(true).setShader(shader);
                canvas.drawRRect(panel, paint);
            }
        } catch (RuntimeException exception) {
            // Unsupported drivers keep the Material tint instead of crashing every frame.
            disable(exception);
        }
    }

    private static float finiteClamp(float value, float minimum, float maximum, float fallback) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }

    /** Release frame-owned GPU snapshots after submitting drawing to Skia. */
    public static void endFrame() {
        if (sceneShader != null) {
            sceneShader.close();
            sceneShader = null;
        }
        if (sceneImage != null) {
            sceneImage.close();
            sceneImage = null;
        }
    }

    private static void disable(RuntimeException exception) {
        unavailable = true;
        endFrame();
        PupperLogger.error("Glass", "Scene glass unavailable; using the Material tint without refraction", exception);
    }

    public static void releaseResources() {
        endFrame();
        if (effect != null) {
            effect.close();
            effect = null;
        }
        unavailable = false;
    }
}
