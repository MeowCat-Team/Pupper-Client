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

/** A protocol observation, including whether a real in-client translator is available. */
public record ProtocolSnapshot(String name, int number, boolean version1_8, boolean translationAvailable) {
    public ProtocolSnapshot {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("Protocol name is blank");
        if (version1_8 && !translationAvailable) {
            throw new IllegalArgumentException("Legacy animation compatibility requires an actual translator");
        }
    }
}
