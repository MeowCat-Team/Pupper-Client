package cn.pupperclient.mixin.mixins.minecraft.client;

import cn.pupperclient.PupperClient;
import cn.pupperclient.event.client.MouseClickEvent;
import cn.pupperclient.management.mod.impl.hud.CPSDisplayMod;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.MouseScrollEvent;
import cn.pupperclient.management.mod.settings.impl.KeybindSetting;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

@Mixin(MouseHandler.class)
public abstract class MixinMouse {

    @Inject(method = "onButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V", shift = At.Shift.AFTER))
    private void onButtonPressed(long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        int button = rawButtonInfo.button();
        if (action == GLFW.GLFW_PRESS) {
            for (KeybindSetting s : PupperClient.getInstance().getModManager().getKeybindSettings()) {
                if (s.getKey().equals(Type.MOUSE.getOrCreate(button))) {
                    s.setPressed();
                    s.setKeyDown(true);
                }
            }
        }
    }

    @Inject(method = "onButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V", shift = At.Shift.AFTER))
    private void onButtonReleased(long window, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        int button = rawButtonInfo.button();
        if (action == GLFW.GLFW_RELEASE) {
            for (KeybindSetting s : PupperClient.getInstance().getModManager().getKeybindSettings()) {
                if (s.getKey().equals(Type.MOUSE.getOrCreate(button))) {
                    s.setKeyDown(false);
                }
            }
        }
    }

    @Inject(method = "onButton", at = @At("HEAD"))
    private void onMouseButtonEvent(long window, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS) {
            Minecraft client = Minecraft.getInstance();

            double rawX = client.mouseHandler.xpos();
            double rawY = client.mouseHandler.ypos();

            Window win = client.getWindow();
            double scaleFactor = win.getGuiScale();
            double scaledX = rawX / scaleFactor;
            double scaledY = win.getGuiScaledHeight() - (rawY / scaleFactor);

            EventBus.getInstance().post(new MouseClickEvent(rawButtonInfo.button(), scaledX, scaledY));
        }
    }

    @Inject(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"), cancellable = true)
    private void onMouseScroll(long window, double x_offset, double y_offset, CallbackInfo ci) {
        MouseScrollEvent event = new MouseScrollEvent(y_offset);
        EventBus.getInstance().post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "onButton", at = @At("HEAD"))
    private void onMouseButtonForCPS(long window, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) return;

        PupperClient.getInstance().getModManager().getMods()
            .stream()
            .filter(mod -> mod instanceof CPSDisplayMod)
            .map(mod -> (CPSDisplayMod) mod)
            .findFirst().ifPresent(cpsDisplayMod -> cpsDisplayMod.onMouseClick(rawButtonInfo.button(), true));

    }
}
