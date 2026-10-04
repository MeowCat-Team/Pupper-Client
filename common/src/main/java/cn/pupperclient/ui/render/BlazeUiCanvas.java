package cn.pupperclient.ui.render;

import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.*;
import java.util.*;

/** Records ordered UI geometry. All GPU work is performed later through Blaze3D. */
public final class BlazeUiCanvas implements UiCanvas {
    static final int MAX_CLIPS = 16;
    static final int VERTEX_FLOATS = 14;
    private static final float[] IDENTITY = {1, 0, 0, 1, 0, 0};
    static final int SHAPE = 0, IMAGE = 1, GLASS = 2, SHADOW = 3, LINEAR = 4, RADIAL = 5, ARC = 6;
    record Clip(float[] inverse, Rect bounds, float[] radii, boolean difference) {}
    static final class Group {
        final List<Object> commands = new ArrayList<>();
        final Rect bounds;
        final float alpha;
        Group(Rect bounds, float alpha) { this.bounds = bounds; this.alpha = alpha; }
    }
    static final class Draw {
        final int mode;
        final GpuTextureView texture;
        final List<Clip> clips;
        final Rect bounds;
        final float[] radii, style, axis, stops;
        final int[] colors;
        float[] vertices = new float[VERTEX_FLOATS * 24];
        int size;
        Draw(int mode, GpuTextureView texture, List<Clip> clips, Rect bounds, float[] radii,
             float[] style, float[] axis, int[] colors, float[] stops) {
            this.mode = mode; this.texture = texture; this.clips = clips; this.bounds = bounds;
            this.radii = radii; this.style = style; this.axis = axis; this.colors = colors; this.stops = stops;
        }
        void vertex(float[] m, float x, float y, float u, float v, int color, Rect uv) {
            if (size + VERTEX_FLOATS > vertices.length) vertices = Arrays.copyOf(vertices, vertices.length * 2);
            vertices[size++] = m[0] * x + m[1] * y + m[4]; vertices[size++] = m[2] * x + m[3] * y + m[5];
            vertices[size++] = x; vertices[size++] = y; vertices[size++] = u; vertices[size++] = v;
            vertices[size++] = ((color >>> 16) & 255) / 255f; vertices[size++] = ((color >>> 8) & 255) / 255f;
            vertices[size++] = (color & 255) / 255f; vertices[size++] = (color >>> 24) / 255f;
            vertices[size++] = Math.min(uv.getLeft(), uv.getRight()); vertices[size++] = Math.min(uv.getTop(), uv.getBottom());
            vertices[size++] = Math.max(uv.getLeft(), uv.getRight()); vertices[size++] = Math.max(uv.getTop(), uv.getBottom());
        }
    }
    private static final class State {
        final float[] matrix;
        List<Clip> clips;
        final Group group;
        State(float[] matrix, List<Clip> clips, Group group) { this.matrix = matrix; this.clips = clips; this.group = group; }
        State copy(Group group) { return new State(matrix.clone(), clips, group); }
    }
    private final UiAssets assets;
    private final int width, height;
    private final boolean glass;
    private final Deque<State> stack = new ArrayDeque<>();
    final Group root;
    private State state;
    private int clearColor;

    BlazeUiCanvas(UiAssets assets, int width, int height, boolean glass) {
        this.assets = assets; this.width = width; this.height = height; this.glass = glass;
        root = new Group(Rect.makeWH(width, height), 1);
        state = new State(new float[]{1, 0, 0, 1, 0, 0}, List.of(), root);
    }
    @Override public int save() { int count = getSaveCount(); stack.push(state); state = state.copy(state.group); return count; }
    @Override public int saveLayer(Rect bounds, Paint paint) {
        int count = getSaveCount();
        Group group = new Group(bounds == null ? Rect.makeWH(width, height) : deviceBounds(bounds), paint == null ? 1 : paint.getAlphaf());
        state.group.commands.add(group); stack.push(state); state = state.copy(group); return count;
    }
    @Override public void restore() { if (!stack.isEmpty()) state = stack.pop(); }
    @Override public void restoreToCount(int count) { while (getSaveCount() > Math.max(1, count)) restore(); }
    @Override public int getSaveCount() { return stack.size() + 1; }
    @Override public void scale(float x, float y) { var m = state.matrix; m[0] *= x; m[1] *= y; m[2] *= x; m[3] *= y; }
    @Override public void translate(float x, float y) { var m = state.matrix; m[4] += m[0] * x + m[1] * y; m[5] += m[2] * x + m[3] * y; }
    @Override public void rotate(float degrees) {
        double r = Math.toRadians(degrees); float c = (float) Math.cos(r), s = (float) Math.sin(r); var m = state.matrix;
        float a = m[0], b = m[1], d = m[2], e = m[3];
        m[0] = a * c + b * s; m[1] = b * c - a * s; m[2] = d * c + e * s; m[3] = e * c - d * s;
    }
    @Override public Matrix33 getLocalToDeviceAsMatrix33() {
        var m = state.matrix; return new Matrix33(m[0], m[1], m[4], m[2], m[3], m[5], 0, 0, 1);
    }
    @Override public void clipRect(Rect rect) { clip(rect, new float[4], false); }
    @Override public void clipPath(Path path, ClipMode mode, boolean aa) {
        RRect rr = path.isRRect();
        if (rr != null) { clip(rr, radii(rr), mode == ClipMode.DIFFERENCE); return; }
        Rect rect = path.isRect();
        if (rect != null) { clip(rect, new float[4], mode == ClipMode.DIFFERENCE); return; }
        Rect oval = path.isOval();
        if (oval != null && Math.abs(oval.getWidth() - oval.getHeight()) < .01f) {
            float r = oval.getWidth() / 2; clip(oval, new float[]{r, r, r, r}, mode == ClipMode.DIFFERENCE); return;
        }
        throw new IllegalArgumentException("UI clip must be a rectangle, rounded rectangle or circle");
    }
    private void clip(Rect bounds, float[] radii, boolean difference) {
        if (state.clips.size() == MAX_CLIPS) throw new IllegalStateException("UI clip nesting exceeds " + MAX_CLIPS);
        var m = state.matrix; float determinant = m[0] * m[3] - m[1] * m[2];
        float[] inverse;
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1e-8f) {
            inverse = new float[]{1, 0, 0, 1, 0, 0}; bounds = Rect.makeWH(0, 0); difference = false;
        } else inverse = new float[]{m[3] / determinant, -m[1] / determinant, -m[2] / determinant, m[0] / determinant,
                (m[1] * m[5] - m[3] * m[4]) / determinant, (m[2] * m[4] - m[0] * m[5]) / determinant};
        var clips = new ArrayList<>(state.clips); clips.add(new Clip(inverse, bounds, radii, difference)); state.clips = List.copyOf(clips);
    }
    @Override public boolean quickReject(Rect rect) {
        Rect b = deviceBounds(rect); return b.getRight() <= 0 || b.getBottom() <= 0 || b.getLeft() >= width || b.getTop() >= height;
    }
    Rect deviceBounds(Rect rect) {
        var m = state.matrix; float l = Float.POSITIVE_INFINITY, t = l, r = Float.NEGATIVE_INFINITY, b = r;
        for (int i = 0; i < 4; i++) {
            float x = i % 2 == 0 ? rect.getLeft() : rect.getRight(), y = i < 2 ? rect.getTop() : rect.getBottom();
            float px = m[0] * x + m[1] * y + m[4], py = m[2] * x + m[3] * y + m[5];
            l = Math.min(l, px); r = Math.max(r, px); t = Math.min(t, py); b = Math.max(b, py);
        }
        return Rect.makeLTRB(l, t, r, b);
    }
    @Override public void clear(int color) { state.group.commands.clear(); if (state.group == root) clearColor = color; }
    int clearColor() { return clearColor; }
    @Override public void drawRect(Rect rect, Paint paint) { shape(rect, new float[4], paint); }
    @Override public void drawRRect(RRect rect, Paint paint) { shape(rect, radii(rect), paint); }
    private void shape(Rect rect, float[] radii, Paint paint) {
        if (rect.isEmpty()) return;
        float stroke = paint.getMode() == PaintMode.STROKE ? Math.max(.01f, paint.getStrokeWidth()) : 0;
        Draw draw = draw(SHAPE, null, rect, radii, new float[]{stroke, 0, 0, 0}, null, null, null);
        quad(draw, expand(rect, stroke / 2 + 1), Rect.makeWH(1, 1), paint.getColor());
    }
    @Override public void drawCircle(float x, float y, float radius, Paint paint) {
        if (radius <= 0) return;
        shape(Rect.makeXYWH(x - radius, y - radius, radius * 2, radius * 2),
                new float[]{radius, radius, radius, radius}, paint);
    }
    @Override public void drawLine(float x, float y, float ex, float ey, Paint paint) {
        float length = (float) Math.hypot(ex - x, ey - y), stroke = Math.max(.01f, paint.getStrokeWidth());
        save(); translate(x, y); rotate((float) Math.toDegrees(Math.atan2(ey - y, ex - x)));
        try (var linePaint = new Paint().setColor(paint.getColor())) { drawRect(Rect.makeXYWH(0, -stroke / 2, length, stroke), linePaint); }
        restore();
    }
    @Override public void drawArc(float l, float t, float r, float b, float start, float sweep, boolean center, Paint paint) {
        Rect bounds = Rect.makeLTRB(l, t, r, b); float radius = Math.min(bounds.getWidth(), bounds.getHeight()) / 2;
        Draw draw = draw(ARC, null, bounds, new float[]{radius, radius, radius, radius},
                new float[]{Math.max(.01f, paint.getStrokeWidth()), (float) Math.toRadians(start), (float) Math.toRadians(sweep), center ? 1 : 0}, null, null, null);
        quad(draw, expand(bounds, paint.getStrokeWidth() / 2 + 1), Rect.makeWH(1, 1), paint.getColor());
    }
    @Override public void drawPath(Path path, Paint paint) {
        RRect rr = path.isRRect(); if (rr != null) { drawRRect(rr, paint); return; }
        Rect rect = path.isRect(); if (rect != null) { drawRect(rect, paint); return; }
        Rect oval = path.isOval(); if (oval != null) { drawCircle(oval.getLeft() + oval.getWidth() / 2, oval.getTop() + oval.getHeight() / 2, oval.getWidth() / 2, paint); return; }
        throw new IllegalArgumentException("Unsupported UI path; use explicit GPU primitives");
    }
    @Override public void drawString(String text, float x, float baseline, Font font, Paint paint) {
        if (text.isEmpty() || paint.getAlpha() == 0) return;
        short[] ids = font.getStringGlyphs(text); float[] advances = font.getWidths(ids);
        var m = state.matrix;
        boolean upright = Math.abs(m[1]) < 1e-6f && Math.abs(m[2]) < 1e-6f
                && m[0] > 0 && Math.abs(m[0] - m[3]) < 1e-6f;
        float scale = upright ? m[0] : Math.max(1, Math.min(8,
                (float) Math.ceil(Math.max(Math.hypot(m[0], m[2]), Math.hypot(m[1], m[3])))));
        for (int i = 0; i < ids.length; i++) {
            var sprite = assets.glyph(font, ids[i], scale);
            if (sprite != null) {
                Rect b = sprite.bounds();
                if (upright) {
                    // One atlas texel per screen pixel; retain glyph antialiasing without a
                    // second bilinear filter at fractional baselines, advances or GUI scales.
                    Rect dst = Rect.makeXYWH(Math.round(m[0] * x + m[4]) + b.getLeft(),
                            Math.round(m[3] * baseline + m[5]) + b.getTop(), b.getWidth(), b.getHeight());
                    Draw draw = imageDraw(sprite.texture().view, 0, false, ImageSampling.PIXEL);
                    quad(draw, dst, sprite.uv(), paint.getColor(), IDENTITY);
                } else {
                    Rect dst = Rect.makeXYWH(x + b.getLeft() / scale, baseline + b.getTop() / scale,
                            b.getWidth() / scale, b.getHeight() / scale);
                    image(sprite.texture().view, dst, sprite.uv(), paint.getColor(), 0, false);
                }
            }
            x += advances[i];
        }
    }
    @Override public void drawImageRect(Image image, Rect src, Rect dst, Paint paint, boolean strict, ImageSampling sampling) {
        var sprite = assets.image(image); Rect uv = sprite.uv();
        float w = image.getWidth(), h = image.getHeight();
        Rect crop = Rect.makeLTRB(uv.getLeft() + src.getLeft() / w * uv.getWidth(), uv.getTop() + src.getTop() / h * uv.getHeight(),
                uv.getLeft() + src.getRight() / w * uv.getWidth(), uv.getTop() + src.getBottom() / h * uv.getHeight());
        quad(imageDraw(sprite.texture().view, 0, false, sampling), dst, crop,
                paint == null ? -1 : (paint.getAlpha() << 24) | 0xFFFFFF);
    }
    @Override public void drawTexture(GpuTextureView texture, Rect src, Rect dst, float alpha, ImageSampling sampling) {
        float w = texture.getWidth(0), h = texture.getHeight(0);
        quad(imageDraw(texture, 0, true, sampling), dst,
                Rect.makeLTRB(src.getLeft() / w, src.getTop() / h, src.getRight() / w, src.getBottom() / h),
                (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | 0xFFFFFF);
    }
    @Override public void drawBlurredImage(Image image, Rect dst, float radius) {
        var sprite = assets.image(image); image(sprite.texture().view, dst, sprite.uv(), -1, radius, false);
    }
    private void image(GpuTextureView texture, Rect dst, Rect uv, int color, float blur, boolean straight) {
        quad(imageDraw(texture, blur, straight, ImageSampling.SMOOTH), dst, uv, color);
    }
    private Draw imageDraw(GpuTextureView texture, float blur, boolean straight, ImageSampling sampling) {
        return draw(IMAGE, texture, Rect.makeWH(0, 0), new float[4],
                new float[]{0, blur, straight ? 1 : 0, sampling == ImageSampling.PIXEL ? 1 : 0}, null, null, null);
    }
    @Override public void drawGradient(RRect rect, Point start, Point end, int[] colors, float[] stops, float stroke) {
        if (colors.length < 2 || colors.length > 3) throw new IllegalArgumentException("UI gradient requires two or three stops");
        Draw draw = draw(LINEAR, null, rect, radii(rect), new float[]{stroke, 0, 0, 0},
                new float[]{start.getX(), start.getY(), end.getX(), end.getY()}, colors.clone(),
                stops == null ? (colors.length == 2 ? new float[]{0, 1, 1} : new float[]{0, .5f, 1}) : stops.clone());
        quad(draw, expand(rect, stroke / 2 + 1), Rect.makeWH(1, 1), -1);
    }
    @Override public void drawRadialCircle(float x, float y, float radius, int inner, int outer) {
        if (radius <= 0) return; Rect bounds = Rect.makeXYWH(x - radius, y - radius, radius * 2, radius * 2);
        Draw draw = draw(RADIAL, null, bounds, new float[]{radius, radius, radius, radius}, new float[4], new float[]{x, y, radius, 0}, new int[]{inner, outer}, new float[]{0, 1, 1});
        quad(draw, expand(bounds, 1), Rect.makeWH(1, 1), -1);
    }
    @Override public void drawShadow(RRect rect, float sigma, int color) {
        Draw draw = draw(SHADOW, null, rect, radii(rect), new float[]{0, sigma, 0, 0}, null, null, null);
        quad(draw, expand(rect, sigma * 3 + 1), Rect.makeWH(1, 1), color);
    }
    public void drawGlass(float x, float y, float w, float h, float radius, float softness, float refraction) {
        if (!glass || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(w) || !Float.isFinite(h) || w <= 0 || h <= 0) return;
        Rect rect = Rect.makeXYWH(x, y, w, h);
        float r = Float.isFinite(radius) ? Math.max(0, Math.min(radius, Math.min(w, h) / 2)) : 0;
        float blur = Float.isFinite(softness) ? Math.max(0, Math.min(8, softness)) : 2;
        float bend = Float.isFinite(refraction) ? Math.max(0, Math.min(4, refraction)) : 1.5f;
        Draw draw = draw(GLASS, null, rect, new float[]{r, r, r, r}, new float[]{0, blur, bend, 0}, null, null, null);
        quad(draw, expand(rect, 1), Rect.makeWH(1, 1), -1);
    }
    private Draw draw(int mode, GpuTextureView texture, Rect bounds, float[] radii, float[] style, float[] axis, int[] colors, float[] stops) {
        var commands = state.group.commands;
        if (mode == IMAGE && style[1] == 0 && !commands.isEmpty() && commands.getLast() instanceof Draw previous
                && previous.mode == IMAGE && previous.texture == texture && previous.clips == state.clips
                && Arrays.equals(previous.style, style)) return previous;
        Draw draw = new Draw(mode, texture, state.clips, bounds, radii, style,
                axis == null ? new float[4] : axis, colors == null ? new int[]{-1, -1, -1} : colors, stops == null ? new float[]{0, 1, 1} : stops);
        commands.add(draw); return draw;
    }
    private void quad(Draw draw, Rect rect, Rect uv, int color) {
        quad(draw, rect, uv, color, state.matrix);
    }
    private void quad(Draw draw, Rect rect, Rect uv, int color, float[] matrix) {
        if (!Float.isFinite(rect.getLeft()) || !Float.isFinite(rect.getTop()) || rect.getWidth() <= 0 || rect.getHeight() <= 0) return;
        int[] corners = {0, 1, 2, 0, 2, 3};
        for (int c : corners) draw.vertex(matrix, c == 0 || c == 3 ? rect.getLeft() : rect.getRight(), c < 2 ? rect.getTop() : rect.getBottom(),
                c == 0 || c == 3 ? uv.getLeft() : uv.getRight(), c < 2 ? uv.getTop() : uv.getBottom(), color, uv);
    }
    static float[] radii(RRect rect) {
        float[] r = rect._radii;
        if (r.length == 1) return new float[]{r[0], r[0], r[0], r[0]};
        if (r.length == 2) return new float[]{r[0], r[0], r[0], r[0]};
        if (r.length == 4) return r.clone();
        return new float[]{r[0], r[2], r[4], r[6]};
    }
    private static Rect expand(Rect rect, float pad) { return Rect.makeLTRB(rect.getLeft() - pad, rect.getTop() - pad, rect.getRight() + pad, rect.getBottom() + pad); }
}
