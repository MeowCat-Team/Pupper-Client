package cn.pupperclient.mixin.mixins.minecraft.client.gui;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.server.ChatEvent;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class MixinChatHud {

    @Inject(method = "addClientSystemMessage", at = @At("HEAD"))
    private void onChatMessage(Component message, CallbackInfo ci) {
        String rawMessage = message.getString();
        if (rawMessage.startsWith("§7[§bPupper Client§7]")) return;
        ChatEvent event = new ChatEvent(rawMessage);
        EventBus.getInstance().post(event);
    }
}
