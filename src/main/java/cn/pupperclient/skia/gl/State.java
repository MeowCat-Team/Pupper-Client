/*
 * Hina Client
 * Copyright (C) 2026 Hina Client
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * This file is part of https://github.com/Lyzev/Skija.
 *
 * Copyright (c) 2025. Lyzev
 *
 * Skija is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License, or
 * (at your option) any later version.
 */

/*
 *  Converted to Java by oneachina
 */

package cn.pupperclient.skia.gl;

import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL45.*;

/**
 * Represents the OpenGL state.
 */
public class State {
    private final int glVersion;
    private final Properties props = new Properties();

    public State(int glVersion) {
        this.glVersion = glVersion;
    }

    /**
     * Saves the current OpenGL state.
     *
     * @see #pop()
     */
    public State push() {
        glGetIntegerv(GL_ACTIVE_TEXTURE, props.lastActiveTexture);
        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, props.lastDrawFramebuffer);
        glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, props.lastReadFramebuffer);
        glActiveTexture(GL_TEXTURE0);
        glGetIntegerv(GL_CURRENT_PROGRAM, props.lastProgram);
        glGetIntegerv(GL_TEXTURE_BINDING_2D, props.lastTexture);

        if (glVersion >= 330 || GL.getCapabilities().GL_ARB_sampler_objects) {
            glGetIntegerv(GL_SAMPLER_BINDING, props.lastSampler);
        }

        glGetIntegerv(GL_ARRAY_BUFFER_BINDING, props.lastArrayBuffer);
        glGetIntegerv(GL_VERTEX_ARRAY_BINDING, props.lastVertexArrayObject);

        if (glVersion >= 200) {
            glGetIntegerv(GL_POLYGON_MODE, props.lastPolygonMode);
        }

        glGetIntegerv(GL_VIEWPORT, props.lastViewport);
        glGetIntegerv(GL_SCISSOR_BOX, props.lastScissorBox);
        glGetIntegerv(GL_BLEND_SRC_RGB, props.lastBlendSrcRgb);
        glGetIntegerv(GL_BLEND_DST_RGB, props.lastBlendDstRgb);
        glGetIntegerv(GL_BLEND_SRC_ALPHA, props.lastBlendSrcAlpha);
        glGetIntegerv(GL_BLEND_DST_ALPHA, props.lastBlendDstAlpha);
        glGetIntegerv(GL_BLEND_EQUATION_RGB, props.lastBlendEquationRgb);
        glGetIntegerv(GL_BLEND_EQUATION_ALPHA, props.lastBlendEquationAlpha);

        props.setLastEnableBlend(glIsEnabled(GL_BLEND));
        props.setLastEnableCullFace(glIsEnabled(GL_CULL_FACE));
        props.setLastEnableDepthTest(glIsEnabled(GL_DEPTH_TEST));
        props.setLastEnableStencilTest(glIsEnabled(GL_STENCIL_TEST));
        props.setLastEnableScissorTest(glIsEnabled(GL_SCISSOR_TEST));
        props.setLastEnableFramebufferSrgb(glIsEnabled(GL_FRAMEBUFFER_SRGB));
        glGetBooleanv(GL_COLOR_WRITEMASK, props.lastColorMask);

        if (glVersion >= 310) {
            props.setLastEnablePrimitiveRestart(glIsEnabled(GL_PRIMITIVE_RESTART));
        }

        props.setLastDepthMask(glGetBoolean(GL_DEPTH_WRITEMASK));

        glGetIntegerv(GL_PIXEL_UNPACK_BUFFER_BINDING, props.lastPixelUnpackBufferBinding);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);

        glGetIntegerv(GL_PACK_SWAP_BYTES, props.lastPackSwapBytes);
        glGetIntegerv(GL_PACK_LSB_FIRST, props.lastPackLsbFirst);
        glGetIntegerv(GL_PACK_ROW_LENGTH, props.lastPackRowLength);
        glGetIntegerv(GL_PACK_SKIP_PIXELS, props.lastPackSkipPixels);
        glGetIntegerv(GL_PACK_SKIP_ROWS, props.lastPackSkipRows);
        glGetIntegerv(GL_PACK_ALIGNMENT, props.lastPackAlignment);

        glGetIntegerv(GL_UNPACK_SWAP_BYTES, props.lastUnpackSwapBytes);
        glGetIntegerv(GL_UNPACK_LSB_FIRST, props.lastUnpackLsbFirst);
        glGetIntegerv(GL_UNPACK_ALIGNMENT, props.lastUnpackAlignment);
        glGetIntegerv(GL_UNPACK_ROW_LENGTH, props.lastUnpackRowLength);
        glGetIntegerv(GL_UNPACK_SKIP_PIXELS, props.lastUnpackSkipPixels);
        glGetIntegerv(GL_UNPACK_SKIP_ROWS, props.lastUnpackSkipRows);

        if (glVersion >= 120) {
            glGetIntegerv(GL_PACK_IMAGE_HEIGHT, props.lastPackImageHeight);
            glGetIntegerv(GL_PACK_SKIP_IMAGES, props.lastPackSkipImages);
            glGetIntegerv(GL_UNPACK_IMAGE_HEIGHT, props.lastUnpackImageHeight);
            glGetIntegerv(GL_UNPACK_SKIP_IMAGES, props.lastUnpackSkipImages);
        }

        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);

        return this;
    }

    /**
     * Restores the state that was saved with {@link #push()}.
     *
     * @see #push()
     */
    public State pop() {
        glUseProgram(props.lastProgram[0]);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, props.lastTexture[0]);

        if (glVersion >= 330 || GL.getCapabilities().GL_ARB_sampler_objects) {
            glBindSampler(0, props.lastSampler[0]);
        }

        glActiveTexture(props.lastActiveTexture[0]);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, props.lastDrawFramebuffer[0]);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, props.lastReadFramebuffer[0]);
        glBindVertexArray(props.lastVertexArrayObject[0]);
        glBindBuffer(GL_ARRAY_BUFFER, props.lastArrayBuffer[0]);

        glBlendEquationSeparate(props.lastBlendEquationRgb[0], props.lastBlendEquationAlpha[0]);
        glBlendFuncSeparate(
            props.lastBlendSrcRgb[0],
            props.lastBlendDstRgb[0],
            props.lastBlendSrcAlpha[0],
            props.lastBlendDstAlpha[0]
        );

        if (props.isLastEnableBlend()) glEnable(GL_BLEND); else glDisable(GL_BLEND);
        if (props.isLastEnableCullFace()) glEnable(GL_CULL_FACE); else glDisable(GL_CULL_FACE);
        if (props.isLastEnableDepthTest()) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
        if (props.isLastEnableStencilTest()) glEnable(GL_STENCIL_TEST); else glDisable(GL_STENCIL_TEST);
        if (props.isLastEnableScissorTest()) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
        if (props.isLastEnableFramebufferSrgb()) glEnable(GL_FRAMEBUFFER_SRGB); else glDisable(GL_FRAMEBUFFER_SRGB);
        glColorMask(props.lastColorMask.get(0) != 0, props.lastColorMask.get(1) != 0,
            props.lastColorMask.get(2) != 0, props.lastColorMask.get(3) != 0);

        if (glVersion >= 310) {
            if (props.isLastEnablePrimitiveRestart()) glEnable(GL_PRIMITIVE_RESTART);
            else glDisable(GL_PRIMITIVE_RESTART);
        }

        if (glVersion >= 200) {
            glPolygonMode(GL_FRONT_AND_BACK, props.lastPolygonMode[0]);
        }

        glViewport(props.lastViewport[0], props.lastViewport[1], props.lastViewport[2], props.lastViewport[3]);
        glScissor(props.lastScissorBox[0], props.lastScissorBox[1], props.lastScissorBox[2], props.lastScissorBox[3]);

        glPixelStorei(GL_PACK_SWAP_BYTES, props.lastPackSwapBytes[0]);
        glPixelStorei(GL_PACK_LSB_FIRST, props.lastPackLsbFirst[0]);
        glPixelStorei(GL_PACK_ROW_LENGTH, props.lastPackRowLength[0]);
        glPixelStorei(GL_PACK_SKIP_PIXELS, props.lastPackSkipPixels[0]);
        glPixelStorei(GL_PACK_SKIP_ROWS, props.lastPackSkipRows[0]);
        glPixelStorei(GL_PACK_ALIGNMENT, props.lastPackAlignment[0]);

        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, props.lastPixelUnpackBufferBinding[0]);
        glPixelStorei(GL_UNPACK_SWAP_BYTES, props.lastUnpackSwapBytes[0]);
        glPixelStorei(GL_UNPACK_LSB_FIRST, props.lastUnpackLsbFirst[0]);
        glPixelStorei(GL_UNPACK_ALIGNMENT, props.lastUnpackAlignment[0]);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, props.lastUnpackRowLength[0]);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, props.lastUnpackSkipPixels[0]);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, props.lastUnpackSkipRows[0]);

        if (glVersion >= 120) {
            glPixelStorei(GL_PACK_IMAGE_HEIGHT, props.lastPackImageHeight[0]);
            glPixelStorei(GL_PACK_SKIP_IMAGES, props.lastPackSkipImages[0]);
            glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, props.lastUnpackImageHeight[0]);
            glPixelStorei(GL_UNPACK_SKIP_IMAGES, props.lastUnpackSkipImages[0]);
        }

        glDepthMask(props.isLastDepthMask());

        return this;
    }
}
