package cn.pupperclient.skia;

import java.awt.Color;
import java.io.File;

import net.minecraft.resources.Identifier;
import cn.pupperclient.skia.context.SkiaContext;
import cn.pupperclient.skia.image.ImageHelper;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.skija.SaveLayerRec;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

/**
 * Skia Graphics Drawing Utility Class
 * Provides Skia-based 2D graphics drawing API for Pupper Client UI rendering
 * All methods get Canvas through SkiaContext
 */
public class Skia {

    // Image loading helper for managing textures and image resources
    private static final ImageHelper imageHelper = new ImageHelper();
    // Shared Paint object to avoid repeated creation and improve performance
    private static final Paint SHARED_PAINT = new Paint();

    /**
     * Draws a filled rectangle with the specified color.
     * @param x The x-coordinate of the rectangle's top-left corner
     * @param y The y-coordinate of the rectangle's top-left corner
     * @param width The width of the rectangle
     * @param height The height of the rectangle
     * @param color The color to fill the rectangle
     */
    public static void drawRect(float x, float y, float width, float height, Color color) {
        getCanvas().drawRect(Rect.makeXYWH(x, y, width, height), setupPaint(color));
    }

    /**
     * Draws a filled circle with the specified color.
     * @param x The x-coordinate of the circle's center
     * @param y The y-coordinate of the circle's center
     * @param radius The radius of the circle
     * @param color The color to fill the circle
     */
    public static void drawCircle(float x, float y, float radius, Color color) {
        getCanvas().drawCircle(x, y, radius, setupPaint(color));
    }

    /**
     * Draws a circle outline with the specified stroke width and color.
     * @param x The x-coordinate of the circle's center
     * @param y The y-coordinate of the circle's center
     * @param radius The radius of the circle
     * @param strokeWidth The width of the stroke
     * @param color The color of the stroke
     */
    public static void drawCircle(float x, float y, float radius, float strokeWidth, Color color) {
        Paint paint = setupPaint(color);
        paint.setMode(PaintMode.STROKE);
        paint.setStrokeWidth(strokeWidth);
        getCanvas().drawCircle(x, y, radius, paint);
        paint.setMode(PaintMode.FILL); // Reset
    }

    /**
     * Draws a filled rounded rectangle with the specified radius and color.
     * @param x The x-coordinate of the rectangle's top-left corner
     * @param y The y-coordinate of the rectangle's top-left corner
     * @param width The width of the rectangle
     * @param height The height of the rectangle
     * @param radius The corner radius
     * @param color The color to fill the rectangle
     */
    public static void drawRoundedRect(float x, float y, float width, float height, float radius, Color color) {
        getCanvas().drawRRect(RRect.makeXYWH(x, y, width, height, radius), setupPaint(color));
    }

    /**
     * Draws a filled rounded rectangle with varying corner radii.
     * @param x The x-coordinate of the rectangle's top-left corner
     * @param y The y-coordinate of the rectangle's top-left corner
     * @param width The width of the rectangle
     * @param height The height of the rectangle
     * @param topLeft The top-left corner radius
     * @param topRight The top-right corner radius
     * @param bottomRight The bottom-right corner radius
     * @param bottomLeft The bottom-left corner radius
     * @param color The color to fill the rectangle
     */
    public static void drawRoundedRectVarying(float x, float y, float width, float height, float topLeft,
                                              float topRight, float bottomRight, float bottomLeft, Color color) {

        float[] corners = new float[] { topLeft, topLeft, topRight, topRight, bottomRight, bottomRight, bottomLeft,
            bottomLeft };

        getCanvas().drawRRect(RRect.makeComplexXYWH(x, y, width, height, corners), setupPaint(color));
    }

    /**
     * Draws a drop shadow effect behind a rounded rectangle.
     * @param x The x-coordinate of the rectangle's top-left corner
     * @param y The y-coordinate of the rectangle's top-left corner
     * @param width The width of the rectangle
     * @param height The height of the rectangle
     * @param radius The corner radius
     */
    public static void drawShadow(float x, float y, float width, float height, float radius) {

        try (Paint paint = new Paint();
             ImageFilter blur = ImageFilter.makeBlur(2.5F, 2.5F, FilterTileMode.DECAL)) {
            
            paint.setARGB(120, 0, 0, 0);
            paint.setImageFilter(blur);

            save();
            clip(x, y, width, height, radius, ClipMode.DIFFERENCE);
            getCanvas().drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
            restore();
        }
    }

    /** Blurs the already rendered scene inside a rounded HUD panel. */
    public static void drawBackdropBlur(float x, float y, float width, float height, float radius) {
        if (width <= 0 || height <= 0) return;

        try (ImageFilter blur = ImageFilter.makeBlur(8, 8, FilterTileMode.CLAMP)) {
            save();
            try {
                clip(x, y, width, height, radius);
                getCanvas().saveLayer(new SaveLayerRec(Rect.makeXYWH(x, y, width, height), null, blur));
                getCanvas().restore();
            } finally {
                restore();
            }
        }
    }

    /**
     * Draws an outline (stroke) around a rounded rectangle.
     * @param x The x-coordinate of the rectangle's top-left corner
     * @param y The y-coordinate of the rectangle's top-left corner
     * @param width The width of the rectangle
     * @param height The height of the rectangle
     * @param radius The corner radius
     * @param strokeWidth The width of the outline stroke
     * @param color The color of the outline
     */
    public static void drawOutline(float x, float y, float width, float height, float radius, float strokeWidth,
                                   Color color) {

        float halfStroke = strokeWidth / 2;

        try (Path path = Path.makeRRect(RRect.makeXYWH(x + halfStroke, y + halfStroke, width - strokeWidth, height - strokeWidth,
            radius - halfStroke));
             Paint paint = new Paint()) {

            paint.setARGB(color.getAlpha(), color.getRed(), color.getGreen(), color.getBlue());
            paint.setStrokeWidth(strokeWidth);
            paint.setMode(PaintMode.STROKE);

            getCanvas().drawPath(path, paint);
        }
    }

    /**
     * Draws an image from the assets/pupper/ directory.
     * @param path The path to the image file relative to assets/pupper/
     * @param x The x-coordinate of the image's top-left corner
     * @param y The y-coordinate of the image's top-left corner
     * @param width The width to draw the image
     * @param height The height to draw the image
     */
    public static void drawImage(String path, float x, float y, float width, float height) {

        path = "/assets/pupper/" + path;

        if (imageHelper.load(path)) {
            getCanvas().drawImageRect(imageHelper.get(path), Rect.makeXYWH(x, y, width, height));
        }
    }

    /**
     * Draws an image from a texture ID with specified alpha and origin.
     * @param textureId The OpenGL texture ID
     * @param x The x-coordinate of the image's top-left corner
     * @param y The y-coordinate of the image's top-left corner
     * @param width The width to draw the image
     * @param height The height to draw the image
     * @param alpha The alpha transparency (0.0 to 1.0)
     * @param origin The surface origin for the image
     */
    public static void drawImage(int textureId, float x, float y, float width, float height, float alpha,
                                 SurfaceOrigin origin) {

        if (imageHelper.load(textureId, width, height, origin)) {
            try (Paint paint = new Paint()) {
                paint.setAlpha((int) (255 * alpha));
                getCanvas().drawImageRect(imageHelper.get(textureId), Rect.makeXYWH(x, y, width, height), paint);
            }
        }
    }

    /**
     * Draws an image from a texture ID with specified alpha (default origin TOP_LEFT).
     * @param textureId The OpenGL texture ID
     * @param x The x-coordinate of the image's top-left corner
     * @param y The y-coordinate of the image's top-left corner
     * @param width The width to draw the image
     * @param height The height to draw the image
     * @param alpha The alpha transparency (0.0 to 1.0)
     */
    public static void drawImage(int textureId, float x, float y, float width, float height, float alpha) {
        drawImage(textureId, x, y, width, height, alpha, SurfaceOrigin.TOP_LEFT);
    }

    /**
     * Draws an image from a file.
     * @param file The image file
     * @param x The x-coordinate of the image's top-left corner
     * @param y The y-coordinate of the image's top-left corner
     * @param width The width to draw the image
     * @param height The height to draw the image
     */
    public static void drawImage(File file, float x, float y, float width, float height) {
        if (imageHelper.load(file)) {
            getCanvas().drawImageRect(imageHelper.get(file.getName()), Rect.makeXYWH(x, y, width, height));
        }
    }

    /**
     * Draws an image from a texture ID with specified origin.
     * @param textureId The OpenGL texture ID
     * @param x The x-coordinate of the image's top-left corner
     * @param y The y-coordinate of the image's top-left corner
     * @param width The width to draw the image
     * @param height The height to draw the image
     * @param origin The surface origin for the image
     */
    public static void drawImage(int textureId, float x, float y, float width, float height, SurfaceOrigin origin) {

        if (imageHelper.load(textureId, width, height, origin)) {
            getCanvas().drawImageRect(imageHelper.get(textureId), Rect.makeXYWH(x, y, width, height));
        }
    }

    /**
     * Draws an image from a texture ID (default origin TOP_LEFT).
     * @param textureId The OpenGL texture ID
     * @param x The x-coordinate of the image's top-left corner
     * @param y The y-coordinate of the image's top-left corner
     * @param width The width to draw the image
     * @param height The height to draw the image
     */
    public static void drawImage(int textureId, float x, float y, float width, float height) {
        drawImage(textureId, x, y, width, height, SurfaceOrigin.TOP_LEFT);
    }

    /**
     * Draws a rounded image from a texture ID.
     * @param textureId The OpenGL texture ID
     * @param x The x-coordinate of the image's top-left corner
     * @param y The y-coordinate of the image's top-left corner
     * @param width The width to draw the image
     * @param height The height to draw the image
     * @param radius The corner radius for clipping
     */
    public static void drawRoundedImage(int textureId, float x, float y, float width, float height, float radius) {

        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
            save();
            getCanvas().clipPath(path, ClipMode.INTERSECT, true);
            drawImage(textureId, x, y, width, height);
            restore();
        }
    }

    /**
     * Draws a rounded image from a file path.
     * @param filePath The path to the image file
     * @param x The x-coordinate of the image's top-left corner
     * @param y The y-coordinate of the image's top-left corner
     * @param width The width to draw the image
     * @param height The height to draw the image
     * @param radius The corner radius for clipping
     */
    public static void drawRoundedImage(String filePath, float x, float y, float width, float height, float radius) {

        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
            save();
            getCanvas().clipPath(path, ClipMode.INTERSECT, true);
            drawImage(filePath, x, y, width, height);
            restore();
        }
    }

    /**
     * Draws a rounded image from a file.
     * @param file The image file
     * @param x The x-coordinate of the image's top-left corner
     * @param y The y-coordinate of the image's top-left corner
     * @param width The width to draw the image
     * @param height The height to draw the image
     * @param radius The corner radius for clipping
     */
    public static void drawRoundedImage(File file, float x, float y, float width, float height, float radius) {

        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
            save();
            getCanvas().clipPath(path, ClipMode.INTERSECT, true);
            drawImage(file, x, y, width, height);
            restore();
        }
    }

    /**
     * Draws a rounded image from a texture ID with alpha and origin.
     * @param textureId The OpenGL texture ID
     * @param x The x-coordinate of the image's top-left corner
     * @param y The y-coordinate of the image's top-left corner
     * @param width The width to draw the image
     * @param height The height to draw the image
     * @param radius The corner radius for clipping
     * @param alpha The alpha transparency (0.0 to 1.0)
     * @param origin The surface origin for the image
     */
    public static void drawRoundedImage(int textureId, float x, float y, float width, float height, float radius,
                                        float alpha, SurfaceOrigin origin) {
        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
            save();
            getCanvas().clipPath(path, ClipMode.INTERSECT, true);
            drawImage(textureId, x, y, width, height, alpha, origin);
            restore();
        }
    }

    /**
     * Draws a rounded image from a texture ID with alpha (default origin TOP_LEFT).
     * @param textureId The OpenGL texture ID
     * @param x The x-coordinate of the image's top-left corner
     * @param y The y-coordinate of the image's top-left corner
     * @param width The width to draw the image
     * @param height The height to draw the image
     * @param radius The corner radius for clipping
     * @param alpha The alpha transparency (0.0 to 1.0)
     */
    public static void drawRoundedImage(int textureId, float x, float y, float width, float height, float radius,
                                        float alpha) {
        drawRoundedImage(textureId, x, y, width, height, radius, alpha, SurfaceOrigin.TOP_LEFT);
    }

    /**
     * Draws a player's head from a skin file with rounded corners.
     * @param file The skin file
     * @param x The x-coordinate of the head's top-left corner
     * @param y The y-coordinate of the head's top-left corner
     * @param width The width to draw the head
     * @param height The height to draw the head
     * @param radius The corner radius for clipping
     */
    public static void drawPlayerHead(File file, float x, float y, float width, float height, float radius) {
        if (imageHelper.load(file)) {

            try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
                Rect srcRect = Rect.makeXYWH(8, 8, 8, 8);
                Rect srcRect1 = Rect.makeXYWH(40, 8, 8, 8);
                Rect dstRect = Rect.makeXYWH(x, y, width, height);

                save();
                getCanvas().clipPath(path, ClipMode.INTERSECT, true);
                getCanvas().drawImageRect(imageHelper.get(file.getName()), srcRect, dstRect, null, false);
                getCanvas().drawImageRect(imageHelper.get(file.getName()), srcRect1, dstRect, null, false);
                restore();
            }
        }
    }

    /**
     * Draws a full player skin at the specified position and scale.
     * @param file The skin file
     * @param x The x-coordinate of the skin's top-left corner
     * @param y The y-coordinate of the skin's top-left corner
     * @param scale The scale factor for the skin
     */
    public static void drawSkin(File file, float x, float y, float scale) {
        if (imageHelper.load(file)) {

            Rect head = Rect.makeXYWH(8, 8, 8, 8);
            Rect headLayer = Rect.makeXYWH(40, 8, 8, 8);
            Rect body = Rect.makeXYWH(20, 20, 8, 12);
            Rect bodyLayer = Rect.makeXYWH(20, 36, 8, 12);
            Rect leftArm = Rect.makeXYWH(36, 52, 4, 12);
            Rect leftArmLayer = Rect.makeXYWH(52, 52, 4, 12);
            Rect rightArm = Rect.makeXYWH(44, 20, 4, 12);
            Rect rightArmLayer = Rect.makeXYWH(44, 36, 4, 12);
            Rect leftLeg = Rect.makeXYWH(20, 52, 4, 12);
            Rect leftLegLayer = Rect.makeXYWH(4, 52, 4, 12);
            Rect rightLeg = Rect.makeXYWH(4, 20, 4, 12);
            Rect rightLegLayer = Rect.makeXYWH(4, 36, 4, 12);

            save();
            scale(x, y, scale);
            getCanvas().drawImageRect(imageHelper.get(file.getName()), head,
                Rect.makeXYWH(x + leftArm.getWidth(), y, head.getWidth(), head.getHeight()), null, false);
            getCanvas().drawImageRect(imageHelper.get(file.getName()), headLayer,
                Rect.makeXYWH(x + leftArm.getWidth(), y, headLayer.getWidth(), headLayer.getHeight()), null, false);
            getCanvas().drawImageRect(imageHelper.get(file.getName()), body,
                Rect.makeXYWH(x + leftArm.getWidth(), y + head.getHeight(), body.getWidth(), body.getHeight()),
                null, false);
            getCanvas().drawImageRect(imageHelper.get(file.getName()), bodyLayer, Rect.makeXYWH(x + leftArm.getWidth(),
                y + headLayer.getHeight(), bodyLayer.getWidth(), bodyLayer.getHeight()), null, false);
            getCanvas().drawImageRect(imageHelper.get(file.getName()), leftArm,
                Rect.makeXYWH(x, y + head.getHeight(), leftArm.getWidth(), leftArm.getHeight()), null, false);
            getCanvas().drawImageRect(imageHelper.get(file.getName()), leftArmLayer,
                Rect.makeXYWH(x, y + headLayer.getHeight(), leftArmLayer.getWidth(), leftArmLayer.getHeight()),
                null, false);
            getCanvas().drawImageRect(imageHelper.get(file.getName()), rightArm,
                Rect.makeXYWH(x + leftArm.getWidth() + body.getWidth(), y + head.getHeight(), rightArm.getWidth(),
                    rightArm.getHeight()),
                null, false);
            getCanvas().drawImageRect(imageHelper.get(file.getName()), rightArmLayer,
                Rect.makeXYWH(x + leftArmLayer.getWidth() + bodyLayer.getWidth(), y + headLayer.getHeight(),
                    rightArmLayer.getWidth(), rightArmLayer.getHeight()),
                null, false);
            getCanvas().drawImageRect(
                imageHelper.get(file.getName()), leftLeg, Rect.makeXYWH(x + leftArm.getWidth(),
                    y + head.getHeight() + body.getHeight(), leftLeg.getWidth(), leftLeg.getHeight()),
                null, false);
            getCanvas().drawImageRect(imageHelper.get(file.getName()), leftLegLayer,
                Rect.makeXYWH(x + leftArmLayer.getWidth(), y + headLayer.getHeight() + bodyLayer.getHeight(),
                    leftLegLayer.getWidth(), leftLegLayer.getHeight()),
                null, false);
            getCanvas()
                .drawImageRect(imageHelper.get(file.getName()), rightLeg,
                    Rect.makeXYWH(x + leftArm.getWidth() + leftLeg.getWidth(),
                        y + head.getHeight() + body.getHeight(), rightLeg.getWidth(), rightLeg.getHeight()),
                    null, false);
            getCanvas().drawImageRect(imageHelper.get(file.getName()), rightLegLayer,
                Rect.makeXYWH(x + leftArmLayer.getWidth() + leftLegLayer.getWidth(),
                    y + headLayer.getHeight() + bodyLayer.getHeight(), rightLegLayer.getWidth(),
                    rightLegLayer.getHeight()),
                null, false);

            restore();
        }
    }

    /**
     * Draws an image from Minecraft's resources.
     * @param path The path to the image in Minecraft's namespace
     * @param x The x-coordinate of the image's top-left corner
     * @param y The y-coordinate of the image's top-left corner
     * @param width The width to draw the image
     * @param height The height to draw the image
     */
    public static void drawMinecraftImage(String path, float x, float y, float width, float height) {
        Identifier identifier = Identifier.fromNamespaceAndPath("minecraft", path);

        if (imageHelper.load(identifier)) {
            getCanvas().drawImageRect(imageHelper.get(identifier.toString()), Rect.makeXYWH(x, y, width, height));
        }
    }

    /**
     * Draws an arc (partial circle outline) with the specified parameters.
     * @param x The x-coordinate of the arc's center
     * @param y The y-coordinate of the arc's center
     * @param radius The radius of the arc
     * @param startAngle The starting angle in degrees
     * @param endAngle The ending angle in degrees
     * @param strokeWidth The width of the arc stroke
     * @param color The color of the arc
     */
    public static void drawArc(float x, float y, float radius, float startAngle, float endAngle, float strokeWidth,
                               Color color) {

        Paint paint = setupPaint(color);
        paint.setStrokeWidth(strokeWidth);
        paint.setMode(PaintMode.STROKE);

        getCanvas().drawArc(x - radius, y - radius, x + radius, y + radius, startAngle - 90, endAngle, false, paint);
        paint.setMode(PaintMode.FILL); // Reset
    }

    /**
     * Draws a line between two points with the specified width and color.
     * @param x The x-coordinate of the starting point
     * @param y The y-coordinate of the starting point
     * @param endX The x-coordinate of the ending point
     * @param endY The y-coordinate of the ending point
     * @param width The width of the line
     * @param color The color of the line
     */
    public static void drawLine(float x, float y, float endX, float endY, float width, Color color) {

        Paint paint = setupPaint(color);

        paint.setStroke(true);
        paint.setStrokeWidth(width);
        paint.setAntiAlias(true);

        getCanvas().drawLine(x, y, endX, endY, paint);
        paint.setStroke(false); // Reset
    }

    /**
     * Draws a rounded rectangle with an animated gradient fill.
     * @param x The x-coordinate of the rectangle's top-left corner
     * @param y The y-coordinate of the rectangle's top-left corner
     * @param width The width of the rectangle
     * @param height The height of the rectangle
     * @param radius The corner radius
     * @param color1 The first color of the gradient
     * @param color2 The second color of the gradient
     */
    public static void drawGradientRoundedRect(float x, float y, float width, float height, float radius, Color color1,
                                               Color color2) {

        long currentTime = System.nanoTime();
        double speed = 0.0000000006;
        double tick = (currentTime * speed) % (2 * Math.PI);
        float max = Math.max(width, height);

        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
            float startX = x + width / 2 - (max / 2) * (float) Math.cos(tick);
            float startY = y + height / 2 - (max / 2) * (float) Math.sin(tick);
            float endX = x + width / 2 + (max / 2) * (float) Math.cos(tick);
            float endY = y + height / 2 + (max / 2) * (float) Math.sin(tick);

            int skColor1 = io.github.humbleui.skija.Color.makeARGB(color1.getAlpha(), color1.getRed(), color1.getGreen(),
                color1.getBlue());
            int skColor2 = io.github.humbleui.skija.Color.makeARGB(color2.getAlpha(), color2.getRed(), color2.getGreen(),
                color2.getBlue());

            int skColorMid = io.github.humbleui.skija.Color.makeARGB(color1.getAlpha(),
                (color1.getRed() + color2.getRed()) / 2, (color1.getGreen() + color2.getGreen()) / 2,
                (color1.getBlue() + color2.getBlue()) / 2);

            try (Paint paint = new Paint();
                 Shader shader = Shader.makeLinearGradient(new Point(startX, startY), new Point(endX, endY),
                     new int[] { skColor1, skColorMid, skColor2 }, new float[] { 0, 0.5f, 1 })) {

                paint.setShader(shader);
                getCanvas().drawPath(path, paint);
            }
        }
    }

    /**
     * Clips the drawing area to the specified path.
     * @param path The path to clip to
     * @param mode The clipping mode
     * @param arg Additional clipping argument
     */
    public static void clipPath(Path path, ClipMode mode, boolean arg) {
        getCanvas().clipPath(path, mode, arg);
    }

    /**
     * Clips the drawing area to the specified path (default intersect mode).
     * @param path The path to clip to
     */
    public static void clipPath(Path path) {
        getCanvas().clipPath(path, ClipMode.INTERSECT, true);
    }

    /**
     * Clips the drawing area to a rounded rectangle.
     * @param x The x-coordinate of the rectangle's top-left corner
     * @param y The y-coordinate of the rectangle's top-left corner
     * @param width The width of the rectangle
     * @param height The height of the rectangle
     * @param radius The corner radius
     * @param mode The clipping mode
     */
    public static void clip(float x, float y, float width, float height, float radius, ClipMode mode) {

        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
            clipPath(path, mode, true);
        }
    }

    /**
     * Clips the drawing area to a rounded rectangle with varying corner radii.
     * @param x The x-coordinate of the rectangle's top-left corner
     * @param y The y-coordinate of the rectangle's top-left corner
     * @param width The width of the rectangle
     * @param height The height of the rectangle
     * @param topLeft The top-left corner radius
     * @param topRight The top-right corner radius
     * @param bottomRight The bottom-right corner radius
     * @param bottomLeft The bottom-left corner radius
     */
    public static void clip(float x, float y, float width, float height, float topLeft, float topRight,
                            float bottomRight, float bottomLeft) {

        float[] corners = new float[] { topLeft, topLeft, topRight, topRight, bottomRight, bottomRight, bottomLeft,
            bottomLeft };

        try (Path path = Path.makeRRect(RRect.makeComplexXYWH(x, y, width, height, corners))) {
            clipPath(path, ClipMode.INTERSECT, true);
        }
    }

    /**
     * Clips the drawing area to a rounded rectangle (default intersect mode).
     * @param x The x-coordinate of the rectangle's top-left corner
     * @param y The y-coordinate of the rectangle's top-left corner
     * @param width The width of the rectangle
     * @param height The height of the rectangle
     * @param radius The corner radius
     */
    public static void clip(float x, float y, float width, float height, float radius) {
        clip(x, y, width, height, radius, ClipMode.INTERSECT);
    }

    /**
     * Draws text at the specified position.
     * @param text The text to draw
     * @param x The x-coordinate of the text's baseline start
     * @param y The y-coordinate of the text's baseline
     * @param color The color of the text
     * @param font The font to use
     */
    public static void drawText(String text, float x, float y, Color color, Font font) {
        Rect bounds = font.measureText(text);
        getCanvas().drawString(text, x - bounds.getLeft(), y - bounds.getTop(), font, setupPaint(color));
    }

    /**
     * Draws text centered horizontally at the specified position.
     * @param text The text to draw
     * @param x The x-coordinate of the text's center
     * @param y The y-coordinate of the text's baseline
     * @param color The color of the text
     * @param font The font to use
     */
    public static void drawCenteredText(String text, float x, float y, Color color, Font font) {
        Rect bounds = font.measureText(text);
        getCanvas().drawString(text, x - bounds.getLeft() - (bounds.getWidth() / 2), y - bounds.getTop(), font,
            setupPaint(color));
    }

    /**
     * Draws text centered vertically at the specified position.
     * @param text The text to draw
     * @param x The x-coordinate of the text's baseline start
     * @param y The y-coordinate of the text's vertical center
     * @param color The color of the text
     * @param font The font to use
     */
    public static void drawHeightCenteredText(String text, float x, float y, Color color, Font font) {

        FontMetrics metrics = font.getMetrics();
        Rect bounds = font.measureText(text);

        float textCenterY = y + (metrics.getAscent() - metrics.getDescent()) / 2 - metrics.getAscent();

        getCanvas().drawString(text, x - bounds.getLeft(), textCenterY, font, setupPaint(color));
    }

    /**
     * Draws text centered both horizontally and vertically at the specified position.
     * @param text The text to draw
     * @param x The x-coordinate of the text's center
     * @param y The y-coordinate of the text's vertical center
     * @param color The color of the text
     * @param font The font to use
     */
    public static void drawFullCenteredText(String text, float x, float y, Color color, Font font) {

        Rect bounds = font.measureText(text);

        FontMetrics metrics = font.getMetrics();

        float textCenterX = x - bounds.getLeft() - (bounds.getWidth() / 2);
        float textCenterY = y + (metrics.getAscent() - metrics.getDescent()) / 2 - metrics.getAscent();

        getCanvas().drawString(text, textCenterX, textCenterY, font, setupPaint(color));
    }

    /**
     * Gets the bounding rectangle of the specified text with the given font.
     * @param text The text to measure
     * @param font The font to use
     * @return The bounding rectangle of the text
     */
    public static Rect getTextBounds(String text, Font font) {
        return font.measureText(text);
    }

    /**
     * Truncates text to fit within the specified width, adding ellipsis if necessary.
     * @param text The original text
     * @param font The font to use
     * @param width The maximum width
     * @return The truncated text with ellipsis if needed
     */
    public static String getLimitText(String text, Font font, float width) {

        if (text == null || text.isEmpty() || !(width > 0)) return "";
        if (getTextBounds(text, font).getWidth() <= width) return text;
        String ellipsis = "…";
        if (getTextBounds(ellipsis, font).getWidth() > width) return "";
        int low = 0, high = text.codePointCount(0, text.length());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            int end = text.offsetByCodePoints(0, middle);
            if (getTextBounds(text.substring(0, end) + ellipsis, font).getWidth() <= width) low = middle;
            else high = middle - 1;
        }
        return text.substring(0, text.offsetByCodePoints(0, low)) + ellipsis;
    }

    /**
     * Sets up a Paint object with the specified color.
     * @param color The color to set
     * @return The configured Paint object
     */
    public static Paint setupPaint(Color color) {
        SHARED_PAINT.setARGB(color.getAlpha(), color.getRed(), color.getGreen(), color.getBlue());
        return SHARED_PAINT;
    }

    /**
     * Saves the current canvas state (transformation matrix, clip, etc.).
     */
    public static void save() {
        getCanvas().save();
    }

    /**
     * Restores the previously saved canvas state.
     */
    public static void restore() {
        getCanvas().restore();
    }

    /**
     * Scales the canvas by the specified factor from the origin.
     * @param scale The scale factor
     */
    public static void scale(float scale) {
        getCanvas().scale(scale, scale);
    }

    /**
     * Scales the canvas by the specified factor from the specified point.
     * @param x The x-coordinate of the scaling center
     * @param y The y-coordinate of the scaling center
     * @param scale The scale factor
     */
    public static void scale(float x, float y, float scale) {
        getCanvas().translate(x, y);
        getCanvas().scale(scale, scale);
        getCanvas().translate(-x, -y);
    }

    /**
     * Scales the canvas by the specified factor from the center of the given rectangle.
     * @param x The x-coordinate of the rectangle's top-left corner
     * @param y The y-coordinate of the rectangle's top-left corner
     * @param width The width of the rectangle
     * @param height The height of the rectangle
     * @param scale The scale factor
     */
    public static void scale(float x, float y, float width, float height, float scale) {

        float centerX = x + width / 2;
        float centerY = y + height / 2;

        getCanvas().translate(centerX, centerY);
        getCanvas().scale(scale, scale);
        getCanvas().translate(-centerX, -centerY);
    }

    /**
     * Translates (moves) the canvas by the specified offset.
     * @param x The x-offset
     * @param y The y-offset
     */
    public static void translate(float x, float y) {
        getCanvas().translate(x, y);
    }

    /**
     * Rotates the canvas by the specified angle around the center of the given rectangle.
     * @param x The x-coordinate of the rectangle's top-left corner
     * @param y The y-coordinate of the rectangle's top-left corner
     * @param width The width of the rectangle
     * @param height The height of the rectangle
     * @param rotate The rotation angle in degrees
     */
    public static void rotate(float x, float y, float width, float height, float rotate) {

        float centerX = x + width / 2;
        float centerY = y + height / 2;

        getCanvas().translate(centerX, centerY);
        getCanvas().rotate(rotate);
        getCanvas().translate(-centerX, -centerY);
    }

    /**
     * Sets the global alpha (transparency) for subsequent drawing operations.
     * @param alpha The alpha value (0-255)
     */
    public static void setAlpha(int alpha) {
        try (Paint paint = new Paint()) {
            paint.setAlpha(alpha);
            getCanvas().saveLayer(null, paint);
        }
    }

    /**
     * Gets the current Skia Canvas for drawing.
     * @return The Skia Canvas object
     */
    public static Canvas getCanvas() {
        return SkiaContext.getCanvas();
    }

    /**
     * Gets the ImageHelper instance for managing images and textures.
     * @return The ImageHelper instance
     */
    public static ImageHelper getImageHelper() {
        return imageHelper;
    }
}
