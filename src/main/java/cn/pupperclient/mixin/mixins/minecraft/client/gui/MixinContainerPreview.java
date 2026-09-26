package cn.pupperclient.mixin.mixins.minecraft.client.gui;

import cn.pupperclient.gui.tooltip.ContainerPreview;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinContainerPreview extends Screen {
    @Shadow @Nullable protected Slot hoveredSlot;
    @Unique private final ContainerPreview pupper$containerPreview = new ContainerPreview();

    protected MixinContainerPreview(Component title) {
        super(title);
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void pupper$drawContainerPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            CallbackInfo ci) {
        ItemStack hovered = hoveredSlot != null && hoveredSlot.hasItem()
            ? hoveredSlot.getItem() : ItemStack.EMPTY;
        if (pupper$containerPreview.extract(graphics, hovered, mouseX, mouseY, width, height,
            pupper$controlDown())) ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void pupper$ignorePreviewClicks(MouseButtonEvent event, boolean doubled,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (pupper$controlDown() && pupper$containerPreview.contains(event.x(), event.y()))
            cir.setReturnValue(true);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void pupper$clearContainerPreview(CallbackInfo ci) {
        ContainerPreview.captureEnderChest(this);
        pupper$containerPreview.clear();
    }

    @Unique private boolean pupper$controlDown() {
        return InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
            || InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
}
