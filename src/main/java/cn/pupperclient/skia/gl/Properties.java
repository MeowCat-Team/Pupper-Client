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

package cn.pupperclient.skia.gl;

import java.util.BitSet;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;

/*
 *  Converted to Java by oneachina
 */
public class Properties {

    public final int[] lastActiveTexture = new int[1];
    public final int[] lastDrawFramebuffer = new int[1];
    public final int[] lastReadFramebuffer = new int[1];
    public final ByteBuffer lastColorMask = BufferUtils.createByteBuffer(4);
    public final int[] lastProgram = new int[1];
    public final int[] lastTexture = new int[1];
    public final int[] lastSampler = new int[1];
    public final int[] lastArrayBuffer = new int[1];
    public final int[] lastVertexArrayObject = new int[1];
    public final int[] lastPolygonMode = new int[2];
    public final int[] lastViewport = new int[4];
    public final int[] lastScissorBox = new int[4];
    public final int[] lastBlendSrcRgb = new int[1];
    public final int[] lastBlendDstRgb = new int[1];
    public final int[] lastBlendSrcAlpha = new int[1];
    public final int[] lastBlendDstAlpha = new int[1];
    public final int[] lastBlendEquationRgb = new int[1];
    public final int[] lastBlendEquationAlpha = new int[1];

    public final int[] lastPixelUnpackBufferBinding = new int[1];
    public final int[] lastUnpackAlignment = new int[1];
    public final int[] lastUnpackRowLength = new int[1];
    public final int[] lastUnpackSkipPixels = new int[1];
    public final int[] lastUnpackSkipRows = new int[1];
    public final int[] lastPackSwapBytes = new int[1];
    public final int[] lastPackLsbFirst = new int[1];
    public final int[] lastPackRowLength = new int[1];
    public final int[] lastPackImageHeight = new int[1];
    public final int[] lastPackSkipPixels = new int[1];
    public final int[] lastPackSkipRows = new int[1];
    public final int[] lastPackSkipImages = new int[1];
    public final int[] lastPackAlignment = new int[1];
    public final int[] lastUnpackSwapBytes = new int[1];
    public final int[] lastUnpackLsbFirst = new int[1];
    public final int[] lastUnpackImageHeight = new int[1];
    public final int[] lastUnpackSkipImages = new int[1];

    private final BitSet flags = new BitSet(8);

    // Boolean Properties via BitSet

    public boolean isLastEnableBlend() { return flags.get(0); }
    public void setLastEnableBlend(boolean value) { flags.set(0, value); }

    public boolean isLastEnableCullFace() { return flags.get(1); }
    public void setLastEnableCullFace(boolean value) { flags.set(1, value); }

    public boolean isLastEnableDepthTest() { return flags.get(2); }
    public void setLastEnableDepthTest(boolean value) { flags.set(2, value); }

    public boolean isLastEnableStencilTest() { return flags.get(3); }
    public void setLastEnableStencilTest(boolean value) { flags.set(3, value); }

    public boolean isLastEnableScissorTest() { return flags.get(4); }
    public void setLastEnableScissorTest(boolean value) { flags.set(4, value); }

    public boolean isLastEnablePrimitiveRestart() { return flags.get(5); }
    public void setLastEnablePrimitiveRestart(boolean value) { flags.set(5, value); }

    public boolean isLastDepthMask() { return flags.get(6); }
    public void setLastDepthMask(boolean value) { flags.set(6, value); }

    public boolean isLastEnableFramebufferSrgb() { return flags.get(7); }
    public void setLastEnableFramebufferSrgb(boolean value) { flags.set(7, value); }
}
