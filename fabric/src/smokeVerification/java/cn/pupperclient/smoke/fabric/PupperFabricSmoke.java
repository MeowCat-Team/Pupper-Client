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

package cn.pupperclient.smoke.fabric;

import cn.pupperclient.platform.ProtocolAccess;
import cn.pupperclient.platform.ProtocolSnapshot;
import cn.pupperclient.smoke.StartupSmoke;
import net.fabricmc.api.ClientModInitializer;

/**
 * Arms the startup smoke, describing the protocol target it expects.
 *
 * <p>ViaFabricPlus is optional, so the expectation is read the same way the client reads
 * it: by reflection, falling back to the native protocol when the mod is absent.
 */
public final class PupperFabricSmoke implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        StartupSmoke.arm("fabric", PupperFabricSmoke::expectedTarget);
    }

    private static ProtocolSnapshot expectedTarget() {
        try {
            Class<?> facade = Class.forName("com.viaversion.viafabricplus.ViaFabricPlus");
            Object api = facade.getMethod("getImpl").invoke(null);
            Object version = api.getClass().getMethod("getTargetVersion").invoke(api);
            if (version == null) {
                return ProtocolAccess.nativeProtocol();
            }
            String name = (String) version.getClass().getMethod("getName").invoke(version);
            int protocol = (int) version.getClass().getMethod("getVersion").invoke(version);
            return new ProtocolSnapshot(name, protocol, name != null && name.startsWith("1.8"), true);
        } catch (ReflectiveOperationException | RuntimeException absentOrNotReady) {
            return ProtocolAccess.nativeProtocol();
        }
    }
}
