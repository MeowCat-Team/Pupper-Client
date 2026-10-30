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

package cn.pupperclient.fabric;

import cn.pupperclient.platform.ProtocolAccess;
import net.minecraft.SharedConstants;

/**
 * Confirms the client still reports a protocol when ViaFabricPlus is not installed.
 *
 * <p>Run on a classpath with the mod removed: the provider must fall back to the
 * native protocol rather than fail to link.
 */
public final class FabricProtocolAbsentChecks {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();

        require(!installed(), "ViaFabricPlus must not be on this classpath");
        var snapshot = new FabricProtocolProvider().target();
        require(snapshot.equals(ProtocolAccess.nativeProtocol()),
            "Without ViaFabricPlus the provider reports the native protocol");
        require(!snapshot.translationAvailable(), "No translator is claimed without the mod");
        require(!ProtocolAccess.isVersion1_8(), "The 1.8 gate stays off without the mod");

        ProtocolAccess.install(new FabricProtocolProvider());
        require(ProtocolAccess.target().equals(ProtocolAccess.nativeProtocol()),
            "Installing the provider without the mod still resolves");
        System.out.println("Fabric protocol-absent checks passed: " + assertions);
    }

    private static boolean installed() {
        try {
            Class.forName("com.viaversion.viafabricplus.ViaFabricPlus");
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
