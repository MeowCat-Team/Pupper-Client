package cn.pupperclient.skia.gl;

import org.lwjgl.opengl.GL;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import static org.lwjgl.opengl.GL33C.*;

/** Bindings Skia can change outside Minecraft's state cache, including the lightmap texture slot. */
final class GlBindings {
    // Ganesh binds fragment samplers. Vertex-only slots are not touched by Skia.
    private final int[] textures = new int[glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS)];
    private final int[] samplers = new int[textures.length];
    private final int[] uniforms = new int[glGetInteger(GL_MAX_UNIFORM_BUFFER_BINDINGS)];
    private final long[] uniformStarts = new long[uniforms.length], uniformSizes = new long[uniforms.length];
    private final boolean[] blends = new boolean[glGetInteger(GL_MAX_DRAW_BUFFERS)];
    private final java.nio.ByteBuffer masks = org.lwjgl.BufferUtils.createByteBuffer(blends.length * 4);
    private final float[] blendColor = new float[4], clearColor = new float[4];
    private final double[] depthRange = new double[2];
    private final int[] stencil = new int[14];
    private final boolean indexedBlend = GL.getCapabilities().OpenGL40 || GL.getCapabilities().GL_ARB_draw_buffers_blend;
    private final boolean indirectSupported = GL.getCapabilities().OpenGL40 || GL.getCapabilities().GL_ARB_draw_indirect;
    private final int[] blendFunctions = new int[blends.length * 6];
    private static final int[] STENCIL_KEYS = {GL_STENCIL_FUNC, GL_STENCIL_REF, GL_STENCIL_VALUE_MASK, GL_STENCIL_WRITEMASK,
        GL_STENCIL_FAIL, GL_STENCIL_PASS_DEPTH_FAIL, GL_STENCIL_PASS_DEPTH_PASS,
        GL_STENCIL_BACK_FUNC, GL_STENCIL_BACK_REF, GL_STENCIL_BACK_VALUE_MASK, GL_STENCIL_BACK_WRITEMASK,
        GL_STENCIL_BACK_FAIL, GL_STENCIL_BACK_PASS_DEPTH_FAIL, GL_STENCIL_BACK_PASS_DEPTH_PASS};
    private static final int[] BLEND_KEYS = {GL_BLEND_SRC_RGB, GL_BLEND_DST_RGB, GL_BLEND_SRC_ALPHA,
        GL_BLEND_DST_ALPHA, GL_BLEND_EQUATION_RGB, GL_BLEND_EQUATION_ALPHA};
    private int uniformBuffer, packBuffer, renderbuffer, elementBuffer, indirectBuffer, depthFunc, cullFace, frontFace, logicOp, clearStencil;
    private float offsetFactor, offsetUnits, coverageValue, lineWidth;
    private double clearDepth;
    private boolean offset, logic, multisample, alphaCoverage, coverage, coverageInvert, discard, dither, programPointSize;

    void push() {
        for (int i = 0; i < textures.length; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            textures[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
            samplers[i] = glGetInteger(GL_SAMPLER_BINDING);
        }
        uniformBuffer = glGetInteger(GL_UNIFORM_BUFFER_BINDING);
        for (int i = 0; i < uniforms.length; i++) {
            uniforms[i] = glGetIntegeri(GL_UNIFORM_BUFFER_BINDING, i);
            if (uniforms[i] != 0) {
                uniformStarts[i] = glGetInteger64i(GL_UNIFORM_BUFFER_START, i);
                uniformSizes[i] = glGetInteger64i(GL_UNIFORM_BUFFER_SIZE, i);
            }
        }
        for (int i = 0; i < blends.length; i++) {
            blends[i] = glIsEnabledi(GL_BLEND, i);
            masks.position(i * 4); masks.limit(i * 4 + 4);
            glGetBooleani_v(GL_COLOR_WRITEMASK, i, masks);
            if (indexedBlend) for (int k = 0; k < 6; k++) blendFunctions[i * 6 + k] = glGetIntegeri(BLEND_KEYS[k], i);
        }
        masks.clear();
        packBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        renderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING);
        elementBuffer = glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
        if (indirectSupported) indirectBuffer = glGetInteger(org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER_BINDING);
        depthFunc = glGetInteger(GL_DEPTH_FUNC); cullFace = glGetInteger(GL_CULL_FACE_MODE); frontFace = glGetInteger(GL_FRONT_FACE);
        glGetFloatv(GL_BLEND_COLOR, blendColor); glGetDoublev(GL_DEPTH_RANGE, depthRange); glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor);
        clearDepth = glGetDouble(GL_DEPTH_CLEAR_VALUE); clearStencil = glGetInteger(GL_STENCIL_CLEAR_VALUE);
        for (int i = 0; i < STENCIL_KEYS.length; i++) stencil[i] = glGetInteger(STENCIL_KEYS[i]);
        offsetFactor = glGetFloat(GL_POLYGON_OFFSET_FACTOR); offsetUnits = glGetFloat(GL_POLYGON_OFFSET_UNITS);
        offset = glIsEnabled(GL_POLYGON_OFFSET_FILL); logic = glIsEnabled(GL_COLOR_LOGIC_OP); logicOp = glGetInteger(GL_LOGIC_OP_MODE);
        multisample = glIsEnabled(GL_MULTISAMPLE); alphaCoverage = glIsEnabled(GL_SAMPLE_ALPHA_TO_COVERAGE);
        coverage = glIsEnabled(GL_SAMPLE_COVERAGE); discard = glIsEnabled(GL_RASTERIZER_DISCARD);
        coverageValue = glGetFloat(GL_SAMPLE_COVERAGE_VALUE); coverageInvert = glGetBoolean(GL_SAMPLE_COVERAGE_INVERT);
        dither = glIsEnabled(GL_DITHER); programPointSize = glIsEnabled(GL_PROGRAM_POINT_SIZE);
        lineWidth = glGetFloat(GL_LINE_WIDTH);
    }

    void pop() {
        for (int i = 0; i < textures.length; i++) {
            glActiveTexture(GL_TEXTURE0 + i); glBindTexture(GL_TEXTURE_2D, textures[i]); glBindSampler(i, samplers[i]);
        }
        for (int i = 0; i < uniforms.length; i++) {
            if (uniforms[i] == 0 || uniformSizes[i] == 0) glBindBufferBase(GL_UNIFORM_BUFFER, i, uniforms[i]);
            else glBindBufferRange(GL_UNIFORM_BUFFER, i, uniforms[i], uniformStarts[i], uniformSizes[i]);
        }
        glBindBuffer(GL_UNIFORM_BUFFER, uniformBuffer); glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer);
        glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
        if (indirectSupported) glBindBuffer(org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER, indirectBuffer);
        // Element-array bindings belong to the VAO restored by State.pop(). Core VAO 0 has no element buffer.
        if (glGetInteger(GL_VERTEX_ARRAY_BINDING) != 0) glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
        for (int i = 0; i < blends.length; i++) {
            if (blends[i]) glEnablei(GL_BLEND, i); else glDisablei(GL_BLEND, i);
            glColorMaski(i, masks.get(i * 4) != 0, masks.get(i * 4 + 1) != 0, masks.get(i * 4 + 2) != 0, masks.get(i * 4 + 3) != 0);
        }
        restoreBlendFunctions();
        glDepthFunc(depthFunc); glDepthRange(depthRange[0], depthRange[1]); glCullFace(cullFace); glFrontFace(frontFace);
        glBlendColor(blendColor[0], blendColor[1], blendColor[2], blendColor[3]);
        glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]); glClearDepth(clearDepth); glClearStencil(clearStencil);
        for (int face = 0; face < 2; face++) {
            int i = face * 7, side = face == 0 ? GL_FRONT : GL_BACK;
            glStencilFuncSeparate(side, stencil[i], stencil[i + 1], stencil[i + 2]); glStencilMaskSeparate(side, stencil[i + 3]);
            glStencilOpSeparate(side, stencil[i + 4], stencil[i + 5], stencil[i + 6]);
        }
        glPolygonOffset(offsetFactor, offsetUnits); set(GL_POLYGON_OFFSET_FILL, offset); glLogicOp(logicOp); set(GL_COLOR_LOGIC_OP, logic);
        set(GL_MULTISAMPLE, multisample); set(GL_SAMPLE_ALPHA_TO_COVERAGE, alphaCoverage); set(GL_SAMPLE_COVERAGE, coverage);
        glSampleCoverage(coverageValue, coverageInvert);
        set(GL_RASTERIZER_DISCARD, discard);
        set(GL_DITHER, dither); set(GL_PROGRAM_POINT_SIZE, programPointSize); glLineWidth(lineWidth);
    }
    /** Reconcile caches when a UI callback lazily uploads a Minecraft-managed texture. */
    void reconcile(Properties p) {
        // Minecraft 26.2 tracks twelve texture slots; its other slots are backend-owned.
        for (int i = 0; i < Math.min(12, textures.length); i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            GlStateManager._activeTexture(GL_TEXTURE0 + i);
            GlStateManager._bindTexture(textures[i]);
        }
        glActiveTexture(p.lastActiveTexture[0]); GlStateManager._activeTexture(p.lastActiveTexture[0]);
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, p.lastDrawFramebuffer[0]);
        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, p.lastReadFramebuffer[0]);
        if (p.isLastEnableDepthTest()) GlStateManager._enableDepthTest(); else GlStateManager._disableDepthTest();
        GlStateManager._depthFunc(depthFunc); GlStateManager._depthMask(p.isLastDepthMask());
        if (p.isLastEnableCullFace()) GlStateManager._enableCull(); else GlStateManager._disableCull();
        if (p.isLastEnableScissorTest()) GlStateManager._enableScissorTest(); else GlStateManager._disableScissorTest();
        GlStateManager._scissorBox(p.lastScissorBox[0], p.lastScissorBox[1], p.lastScissorBox[2], p.lastScissorBox[3]);
        GlStateManager._blendFuncSeparate(p.lastBlendSrcRgb[0], p.lastBlendDstRgb[0], p.lastBlendSrcAlpha[0], p.lastBlendDstAlpha[0]);
        GlStateManager._blendEquationSeparate(p.lastBlendEquationRgb[0], p.lastBlendEquationAlpha[0]);
        for (int i = 0; i < Math.min(blends.length, ColorTargetState.MAX_COLOR_TARGETS); i++) {
            if (blends[i]) GlStateManager._enableBlend(i); else GlStateManager._disableBlend(i);
            int k = i * 4;
            int mask = (masks.get(k) != 0 ? 1 : 0) | (masks.get(k + 1) != 0 ? 2 : 0)
                | (masks.get(k + 2) != 0 ? 4 : 0) | (masks.get(k + 3) != 0 ? 8 : 0);
            GlStateManager._colorMask(i, mask);
        }
        GlStateManager._polygonOffset(offsetFactor, offsetUnits);
        if (offset) GlStateManager._enablePolygonOffset(); else GlStateManager._disablePolygonOffset();
        GlStateManager._logicOp(logicOp);
        if (logic) GlStateManager._enableColorLogicOp(); else GlStateManager._disableColorLogicOp();
        // Cache setters may apply non-indexed blend factors. Restore attachment-specific factors last.
        restoreBlendFunctions(); glActiveTexture(p.lastActiveTexture[0]);
    }
    private void restoreBlendFunctions() {
        if (!indexedBlend) return;
        for (int i = 0; i < blends.length; i++) {
            int k = i * 6;
            org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFuncSeparateiARB(i, blendFunctions[k], blendFunctions[k + 1], blendFunctions[k + 2], blendFunctions[k + 3]);
            org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendEquationSeparateiARB(i, blendFunctions[k + 4], blendFunctions[k + 5]);
        }
    }
    private static void set(int capability, boolean enabled) { if (enabled) glEnable(capability); else glDisable(capability); }
}
