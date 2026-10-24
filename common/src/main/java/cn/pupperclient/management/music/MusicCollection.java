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

package cn.pupperclient.management.music;

/** A remote artist or playlist, never a playable song or a local editable playlist. */
public record MusicCollection(String provider, String id, MusicSearchType type, String name, String owner,
        String coverUrl, int trackCount) {
    public MusicCollection {
        if (provider == null || !provider.matches("[a-z0-9_-]+") || id == null || !id.matches("[a-zA-Z0-9_-]{1,80}")
                || type == null || type == MusicSearchType.SONGS || name == null || name.isBlank())
            throw new IllegalArgumentException("Invalid music collection");
        owner = owner == null ? "" : owner;
        coverUrl = coverUrl == null ? "" : coverUrl;
        trackCount = Math.max(-1, trackCount);
    }
    public String key() { return provider + ":" + type.name() + ":" + id; }
    public String coverFilename() {
        return "collection-" + provider + "-" + type.name().toLowerCase(java.util.Locale.ROOT) + "-"
            + java.util.HexFormat.of().formatHex(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".jpg";
    }
}
