package cn.pupperclient.smoke.neoforge;

import cn.pupperclient.platform.ProtocolAccess;
import cn.pupperclient.smoke.StartupSmoke;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = "pupper_smoke", dist = Dist.CLIENT)
public final class PupperNeoForgeSmoke {
    public PupperNeoForgeSmoke() {
        StartupSmoke.arm("neoforge", ProtocolAccess::nativeProtocol);
    }
}
