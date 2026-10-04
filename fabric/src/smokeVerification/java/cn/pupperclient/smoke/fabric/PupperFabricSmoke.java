package cn.pupperclient.smoke.fabric;

import cn.pupperclient.platform.ProtocolSnapshot;
import cn.pupperclient.smoke.StartupSmoke;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.fabricmc.api.ClientModInitializer;

public final class PupperFabricSmoke implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        StartupSmoke.arm("fabric", () -> {
            var target = ViaFabricPlus.getImpl().getTargetVersion();
            return new ProtocolSnapshot(target.getName(), target.getVersion(), target.equals(ProtocolVersion.v1_8), true);
        });
    }
}
