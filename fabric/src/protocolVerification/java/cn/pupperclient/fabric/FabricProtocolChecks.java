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
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.api.ViaFabricPlusBase;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;

/** Exercises the real ViaFabricPlus API using a selected-target fixture, without a game connection. */
public final class FabricProtocolChecks {
    private static int assertions;

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        var provider = new FabricProtocolProvider();
        require(!provider.target().translationAvailable(), "Before ViaFabricPlus initialization, the provider reports native capability");

        var selected = new AtomicReference<>(ProtocolVersion.v1_8);
        var fixture = (ViaFabricPlusBase) Proxy.newProxyInstance(ViaFabricPlusBase.class.getClassLoader(),
                new Class<?>[]{ViaFabricPlusBase.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getTargetVersion")) return selected.get();
                    throw new AssertionError("Unexpected ViaFabricPlus API call: " + method.getName());
                });
        ViaFabricPlus.init(fixture);
        ProtocolAccess.install(provider);
        require(ProtocolAccess.target().name().equals(ProtocolVersion.v1_8.getName()), "The HUD keeps ViaFabricPlus's exact target label");
        require(ProtocolAccess.target().number() == ProtocolVersion.v1_8.getVersion(), "The snapshot preserves the actual target protocol");
        require(ProtocolAccess.isVersion1_8(), "The original selected 1.8 animation gate works while disconnected");
        require(ProtocolAccess.target().translationAvailable(), "Initialized ViaFabricPlus exposes actual translation availability");

        selected.set(ProtocolVersion.v1_9);
        require(!ProtocolAccess.isVersion1_8(), "Selecting another version immediately disables 1.8 animation gates");
        require(ProtocolAccess.target().name().equals(ProtocolVersion.v1_9.getName()), "Selecting another version immediately updates the HUD");
        selected.set(null);
        require(ProtocolAccess.target().equals(ProtocolAccess.nativeProtocol()), "A missing selected target reports the actual native protocol");
        ProtocolAccess.install(ProtocolAccess::nativeProtocol);
        System.out.println("Fabric protocol checks passed: " + assertions);
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
