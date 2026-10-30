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
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;

/**
 * Exercises the provider's reflection path against the real ViaFabricPlus API.
 *
 * <p>The check has to build the same objects the mod would: the target version and the
 * API facade are looked up by name, so the check itself never links against the mod.
 */
public final class FabricProtocolChecks {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();

        Class<?> versionType = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
        Class<?> baseType = Class.forName("com.viaversion.viafabricplus.api.ViaFabricPlusBase");
        Class<?> facadeType = Class.forName("com.viaversion.viafabricplus.ViaFabricPlus");

        Object v1_8 = versionType.getField("v1_8").get(null);
        Object v1_9 = versionType.getField("v1_9").get(null);

        var provider = new FabricProtocolProvider();
        require(!provider.target().translationAvailable(),
            "Before ViaFabricPlus initialization, the provider reports native capability");

        var selected = new AtomicReference<>(v1_8);
        Object fixture = Proxy.newProxyInstance(baseType.getClassLoader(), new Class<?>[]{baseType},
            (proxy, method, arguments) -> {
                if (method.getName().equals("getTargetVersion")) return selected.get();
                throw new AssertionError("Unexpected ViaFabricPlus API call: " + method.getName());
            });
        facadeType.getMethod("init", baseType).invoke(null, fixture);

        ProtocolAccess.install(provider);
        String expected = (String) versionType.getMethod("getName").invoke(v1_8);
        int expectedNumber = (int) versionType.getMethod("getVersion").invoke(v1_8);
        require(ProtocolAccess.target().name().equals(expected), "The HUD keeps ViaFabricPlus's exact target label");
        require(ProtocolAccess.target().number() == expectedNumber, "The snapshot preserves the actual target protocol");
        require(ProtocolAccess.isVersion1_8(), "The original selected 1.8 animation gate works while disconnected");
        require(ProtocolAccess.target().translationAvailable(), "Initialized ViaFabricPlus exposes actual translation availability");

        selected.set(v1_9);
        require(!ProtocolAccess.isVersion1_8(), "Selecting another version immediately disables 1.8 animation gates");
        require(ProtocolAccess.target().name().equals(versionType.getMethod("getName").invoke(v1_9)),
            "Selecting another version immediately updates the HUD");

        selected.set(null);
        require(ProtocolAccess.target().equals(ProtocolAccess.nativeProtocol()),
            "A missing selected target reports the actual native protocol");
        ProtocolAccess.install(ProtocolAccess::nativeProtocol);
        System.out.println("Fabric protocol checks passed: " + assertions);
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
