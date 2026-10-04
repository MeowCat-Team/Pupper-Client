package cn.pupperclient.platform;

import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;

/** Verifies selected-target changes and honest native protocol reporting without a game connection. */
public final class ProtocolAccessChecks {
    private static int assertions;

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        ProtocolAccess.install(ProtocolAccess::nativeProtocol);
        var nativeVersion = ProtocolAccess.target();
        require(nativeVersion.name().equals(SharedConstants.getCurrentVersion().name()), "Native version uses Minecraft's actual name");
        require(nativeVersion.number() == SharedConstants.getCurrentVersion().protocolVersion(), "Native version uses Minecraft's actual protocol");
        require(!nativeVersion.translationAvailable(), "Vanilla does not claim cross-version translation");
        require(!ProtocolAccess.isVersion1_8(), "Vanilla does not enable translated 1.8 animations");

        var selected = new AtomicReference<>(new ProtocolSnapshot("1.8.x", 47, true, true));
        ProtocolAccess.install(selected::get);
        require(ProtocolAccess.target().name().equals("1.8.x"), "Selected target is visible without a connection");
        require(ProtocolAccess.isVersion1_8(), "Real translated 1.8 enables the existing legacy animation gates");
        selected.set(new ProtocolSnapshot("26.2", nativeVersion.number(), false, true));
        require(!ProtocolAccess.isVersion1_8(), "Changing the selected target updates animation gates immediately");
        require(ProtocolAccess.target().translationAvailable(), "Native target selection does not erase translator availability");

        expectRejected(() -> ProtocolAccess.install(null), "Null provider is rejected");
        expectRejected(() -> new ProtocolSnapshot(" ", 47, true, true), "Blank protocol labels are rejected");
        expectRejected(() -> new ProtocolSnapshot("1.8.x", 47, true, false), "A native provider cannot claim translated legacy animation support");
        ProtocolAccess.install(ProtocolAccess::nativeProtocol);
        System.out.println("Protocol access checks passed: " + assertions);
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expectRejected(Runnable operation, String message) {
        try {
            operation.run();
        } catch (NullPointerException | IllegalArgumentException expected) {
            assertions++;
            return;
        }
        throw new AssertionError(message);
    }
}
