package cn.pupperclient.mixin.mixins.minecraft.network;

import cn.pupperclient.utils.network.ServerTextContents;
import cn.pupperclient.utils.network.ServerTextPrivacy;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TranslatableContents.class)
public abstract class MixinTranslatableContents implements ServerTextContents {
    @Shadow private Language decomposedWith;
    @Unique private boolean pupper$serverSupplied;
    @Unique private boolean pupper$lastProtectionState;

    @Override
    public void pupper$markServerSupplied() {
        pupper$serverSupplied = true;
    }

    @Inject(method = "decompose", at = @At("HEAD"))
    private void pupper$invalidateChangedPolicy(CallbackInfo ci) {
        boolean protectedNow = pupper$serverSupplied && ServerTextPrivacy.isEnabled();
        if (protectedNow != pupper$lastProtectionState) {
            decomposedWith = null;
            pupper$lastProtectionState = protectedNow;
        }
    }

    @Redirect(method = "decompose", at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"))
    private String pupper$resolveWithFallback(Language language, String key, String fallback) {
        return ServerTextPrivacy.resolveTranslation(language, key, fallback, pupper$serverSupplied);
    }

    @Redirect(method = "decompose", at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;)Ljava/lang/String;"))
    private String pupper$resolveWithoutFallback(Language language, String key) {
        return ServerTextPrivacy.resolveTranslation(language, key, key, pupper$serverSupplied);
    }
}
