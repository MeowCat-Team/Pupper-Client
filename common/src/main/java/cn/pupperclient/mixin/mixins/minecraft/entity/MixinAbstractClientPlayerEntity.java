package cn.pupperclient.mixin.mixins.minecraft.entity;

import cn.pupperclient.PupperClient;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractClientPlayer.class, priority = 2000)
public abstract class MixinAbstractClientPlayerEntity extends Player {
    @Unique
    private boolean shownCape = false;

    public MixinAbstractClientPlayerEntity(Level world, GameProfile gameProfile) {
        super(world, gameProfile);
    }

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    public void getSkinTextures(CallbackInfoReturnable<PlayerSkin> cir) {
        var customCape = PupperClient.getInstance().getCapeManager().getSelectedCapeTexture();
        if (customCape != null) {
            PlayerSkin current = cir.getReturnValue();
            cir.setReturnValue(new PlayerSkin(
                current.body(),
                customCape,
                current.elytra(),
                current.model(),
                current.secure()
            ));
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (DATA_PLAYER_MODE_CUSTOMISATION.equals(data)) {
            boolean showCape = isModelPartShown(PlayerModelPart.CAPE);
            if (showCape != shownCape) {
                shownCape = showCape;
            }
        }
    }
}
