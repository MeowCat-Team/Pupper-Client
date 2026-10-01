package cn.pupperclient.mixin.mixins.minecraft.client;

import cn.pupperclient.PupperClient;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ClientBrandRetriever.class)
public class MixinClientBrandRetriever {
    /**
     * @author oneachina
     * @reason Set Pupper Client Brand
     */
    @Overwrite
    public static String getClientModName() {
        return PupperClient.getInstance().getName() + " (" + PupperClient.getInstance().getVersion() + ") ";
    }
}
