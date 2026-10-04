package cn.pupperclient.utils.render;

import java.util.List;
import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.architectury.platform.Platform;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;

/** Keeps quad order and material boundaries while acquiring each consecutive buffer only once. */
public final class ItemQuadBatcher {
    private static final boolean SODIUM_PRESENT = Platform.isModLoaded("sodium");

    private ItemQuadBatcher() { }

    public static void submit(ItemFeatureRenderer.Submit submit, QuadInstance instance,
                              Function<RenderType, VertexConsumer> buffers) {
        instance.setLightCoords(submit.lightCoords());
        instance.setOverlayCoords(submit.overlayCoords());
        List<BakedQuad> quads = submit.quads();
        PoseStack.Pose pose = submit.pose();
        int[] tints = submit.tintLayers();
        for (int start = 0; start < quads.size();) {
            RenderType type = quads.get(start).materialInfo().itemRenderType();
            int end = start + 1;
            if (type.canConsolidateConsecutiveGeometry()) {
                while (end < quads.size() && quads.get(end).materialInfo().itemRenderType() == type) end++;
            }
            VertexConsumer consumer = buffers.apply(type);
            boolean written = SODIUM_PRESENT && end - start > 1
                    && SodiumItemVertexBatch.write(consumer, pose, quads, start, end, tints, instance);
            if (!written) {
                for (int index = start; index < end; index++) {
                    BakedQuad quad = quads.get(index);
                    instance.setColor(tint(tints, quad.materialInfo()));
                    consumer.putBakedQuad(pose, quad, instance);
                }
            }
            start = end;
        }
    }

    static int tint(int[] tints, BakedQuad.MaterialInfo material) {
        int index = material.tintIndex();
        return material.isTinted() && index >= 0 && index < tints.length ? tints[index] : -1;
    }
}
