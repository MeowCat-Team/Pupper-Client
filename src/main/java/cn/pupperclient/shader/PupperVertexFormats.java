package cn.pupperclient.shader;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.GpuFormat;

public abstract class PupperVertexFormats {
    public static final VertexFormat POS2 = VertexFormat.builder(0)
        .addAttribute("pos", GpuFormat.RG32_FLOAT)
        .build();

    private PupperVertexFormats() {}
}
// based on meteor
