package cn.pupperclient.mixin.mixins.minecraft.client.render;

import cn.pupperclient.management.mod.impl.render.EntityRenderOptimizerMod;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 修船的水面遮罩（water patch）姿态。
 *
 * <p>{@code BoatModel#createWaterPatch()} 声明的
 * {@code PartPose.offsetAndRotation(0, -3, 1, PI/2, 0, 0)} 从来没有生效：
 * <ul>
 *   <li>{@code PartDefinition#bake} 只调用 {@code setInitialPose(...)}，不调用 {@code loadPose(...)}；</li>
 *   <li>{@code ModelPart#resetPose()} 的唯一调用点是 {@code Model#setupAnim(S)}；</li>
 *   <li>{@code Model.Simple#setupAnim(Unit)} 是空实现（字节码只有 {@code return}），不调用 {@code super}。</li>
 * </ul>
 * 结果是水膜以原点、零旋转渲染 —— 一块 28x16x3 的竖直板，而不是贴合船底的水平片。
 *
 * <p>这里在构造器返回前一次性把初始姿势 load 进去。{@code Model.Simple#setupAnim} 不会把它
 * reset 回去，所以只需一次。
 *
 * <p>注意：不要改成让 {@code Model.Simple#setupAnim} 调用 {@code resetPose()} ——
 * {@code OrderedSubmitNodeCollector#submitModelPart} 依赖"调用方已摆好姿势、setupAnim 什么都不做"
 * 的语义，全局改会破坏所有 {@code submitModelPart} 使用者。
 */
@Mixin(BoatRenderer.class)
public abstract class MixinBoatRendererWaterPatchFix {

    @Shadow
    @Final
    private Model.Simple waterPatchModel;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void pupper$applyWaterPatchInitialPose(EntityRendererProvider.Context context,
                                                   ModelLayerLocation modelId, CallbackInfo ci) {
        EntityRenderOptimizerMod mod = EntityRenderOptimizerMod.getInstance();
        if (mod != null && !mod.fixBoatWaterPatch()) return;

        this.waterPatchModel.resetPose();
    }
}
