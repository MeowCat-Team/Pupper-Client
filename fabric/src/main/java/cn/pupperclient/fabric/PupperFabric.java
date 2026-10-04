package cn.pupperclient.fabric;

import cn.pupperclient.platform.ProtocolAccess;
import net.fabricmc.api.ClientModInitializer;

/** Loader setup only; Minecraft's shared startup hook initializes the client once rendering is ready. */
public final class PupperFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ProtocolAccess.install(new FabricProtocolProvider());
    }
}
