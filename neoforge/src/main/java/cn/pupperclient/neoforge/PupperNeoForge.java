package cn.pupperclient.neoforge;

import cn.pupperclient.platform.ProtocolAccess;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/** The same startup hook, managers, mixins and UI are provided by common. */
@Mod(value = "pupper", dist = Dist.CLIENT)
public final class PupperNeoForge {
    public PupperNeoForge() {
        ProtocolAccess.install(ProtocolAccess::nativeProtocol);
    }
}
