/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
