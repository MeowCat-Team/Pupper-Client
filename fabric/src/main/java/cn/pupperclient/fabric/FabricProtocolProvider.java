package cn.pupperclient.fabric;

import cn.pupperclient.platform.ProtocolAccess;
import cn.pupperclient.platform.ProtocolSnapshot;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.api.ViaFabricPlusBase;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

/** Keeps the existing Fabric integration, including its selected target before connecting. */
public final class FabricProtocolProvider implements ProtocolAccess.Provider {
    @Override
    public ProtocolSnapshot target() {
        ViaFabricPlusBase via;
        try {
            via = ViaFabricPlus.getImpl();
        } catch (IllegalStateException notInitialized) {
            // The API throws here during early loading; it does not return null.
            return ProtocolAccess.nativeProtocol();
        }
        var target = via.getTargetVersion();
        if (target == null) return ProtocolAccess.nativeProtocol();
        return new ProtocolSnapshot(target.getName(), target.getVersion(),
                target.equals(ProtocolVersion.v1_8), true);
    }
}
