package cn.pupperclient.platform;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/** Compilation probe for the two annotation properties whose scalar API differs between loaders. */
final class MixinAnnotationFixture {
    @Redirect(method = "probe", at = @At(value = "INVOKE", target = "Ljava/lang/String;length()I"),
        slice = @Slice(from = @At("HEAD"), to = @At("TAIL")))
    private int redirect(String value) {
        return value.length();
    }
}
