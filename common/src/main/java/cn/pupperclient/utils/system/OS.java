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

package cn.pupperclient.utils.system;

public enum OS {
    WINDOWS("win"),
    MACOS("mac"),
    LINUX("nix", "nux", "aix"),
    UNKNOWN();

    private final String[] identifiers;

    OS(String... identifiers) {
        this.identifiers = identifiers;
    }

    private static final OS CURRENT_OS = detectOS();

    private static OS detectOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        return detectOS(osName);
    }

    private static OS detectOS(String osName) {
        return java.util.Arrays.stream(values())
            .filter(os -> os != UNKNOWN)
            .filter(os -> java.util.Arrays.stream(os.identifiers)
                .anyMatch(osName::contains))
            .findFirst()
            .orElse(UNKNOWN);
    }

    public static boolean isWindows() {
        return CURRENT_OS == WINDOWS;
    }

    public static boolean isMacOS() {
        return CURRENT_OS == MACOS;
    }

    public static boolean isLinux() {
        return CURRENT_OS == LINUX;
    }

    public static OS getCurrent() {
        return CURRENT_OS;
    }
}
