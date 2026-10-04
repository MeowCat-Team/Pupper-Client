package cn.pupperclient.platform;

import java.util.Objects;
import net.minecraft.SharedConstants;

/** Shared protocol consumers; translation itself belongs to the installed platform provider. */
public final class ProtocolAccess {
    private static volatile Provider provider = ProtocolAccess::nativeProtocol;

    private ProtocolAccess() { }

    /** Install before client managers start so both loaders use the same feature implementation. */
    public static void install(Provider platformProvider) {
        provider = Objects.requireNonNull(platformProvider, "platformProvider");
    }

    /** The selected target remains visible while disconnected, as it does in ViaFabricPlus. */
    public static ProtocolSnapshot target() {
        return Objects.requireNonNull(provider.target(), "Protocol provider returned no target");
    }

    public static boolean isVersion1_8() {
        return target().version1_8();
    }

    /** Vanilla has no protocol translator, even when connected through an external proxy. */
    public static ProtocolSnapshot nativeProtocol() {
        var version = SharedConstants.getCurrentVersion();
        return new ProtocolSnapshot(version.name(), version.protocolVersion(), false, false);
    }

    @FunctionalInterface
    public interface Provider {
        ProtocolSnapshot target();
    }
}
