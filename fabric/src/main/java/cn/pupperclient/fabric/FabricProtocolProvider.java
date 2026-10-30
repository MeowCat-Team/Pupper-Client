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

import cn.pupperclient.PupperLogger;
import cn.pupperclient.platform.ProtocolAccess;
import cn.pupperclient.platform.ProtocolSnapshot;

/**
 * Reports the protocol target selected in ViaFabricPlus, when it is installed.
 *
 * <p>The integration is optional, so this class never links against the mod: it looks
 * the API up by name and falls back to the native protocol whenever the mod is absent,
 * not yet initialised, or answers with something unexpected. That keeps Pupper Client
 * loadable without ViaFabricPlus while still using it when present.
 */
public final class FabricProtocolProvider implements ProtocolAccess.Provider {

    private static final String FACADE = "com.viaversion.viafabricplus.ViaFabricPlus";
    private static final String BASE = "com.viaversion.viafabricplus.api.ViaFabricPlusBase";

    @Override
    public ProtocolSnapshot target() {
        Object api = api();
        if (api == null) {
            return ProtocolAccess.nativeProtocol();
        }

        Object version;
        try {
            version = api.getClass().getMethod("getTargetVersion").invoke(api);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // A mod version with a different API, or a proxy that rejects the call.
            return ProtocolAccess.nativeProtocol();
        }
        if (version == null) {
            return ProtocolAccess.nativeProtocol();
        }

        try {
            String name = (String) version.getClass().getMethod("getName").invoke(version);
            int protocol = (int) version.getClass().getMethod("getVersion").invoke(version);
            return new ProtocolSnapshot(name, protocol, is1_8(name), true);
        } catch (ReflectiveOperationException | RuntimeException unexpected) {
            PupperLogger.warn("Protocol", "ViaFabricPlus target version is not readable; using native protocol");
            return ProtocolAccess.nativeProtocol();
        }
    }

    /**
     * The API object, or {@code null} when ViaFabricPlus is not installed or not ready.
     *
     * <p>{@code getImpl()} throws while the mod is still loading rather than returning
     * null, so both outcomes have to be handled.
     */
    private static Object api() {
        Class<?> facade;
        try {
            facade = Class.forName(FACADE);
        } catch (ClassNotFoundException absent) {
            return null;
        }

        try {
            Object api = facade.getMethod("getImpl").invoke(null);
            if (api == null || !base().isInstance(api)) {
                return null;
            }
            return api;
        } catch (ReflectiveOperationException | RuntimeException notReady) {
            return null;
        }
    }

    private static Class<?> base() {
        try {
            return Class.forName(BASE);
        } catch (ClassNotFoundException absent) {
            return Object.class;
        }
    }

    /**
     * Whether the target is 1.8, by name.
     *
     * <p>Comparing the enum constant would need the ViaVersion class at link time; the
     * API's name for that version is the stable part.
     */
    private static boolean is1_8(String name) {
        return name != null && name.startsWith("1.8");
    }
}
