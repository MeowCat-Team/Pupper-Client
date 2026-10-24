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

import cn.pupperclient.utils.language.I18n;
import java.util.Locale;

public final class MusicText {
    private MusicText() { }
    public static String get(String key, Object... arguments) {
        String translated = I18n.get(key);
        return arguments.length == 0 ? translated : String.format(Locale.ROOT, translated, arguments);
    }
    public static String time(float seconds) {
        int value = Float.isFinite(seconds) ? Math.max(0, (int) seconds) : 0;
        return String.format(Locale.ROOT, "%d:%02d", value / 60, value % 60);
    }
    public static String access(MusicTrack track) {
        if (track.preview()) {
            String preview = track.previewSeconds() > 0 ? get("music.access.preview.seconds", track.previewSeconds()) : get("music.access.preview");
            return track.fee() == 1 ? get("music.access.vip") + " · " + preview : preview;
        }
        return switch (track.fee()) {
            case 0 -> "";
            case 1 -> get("music.access.vip");
            case 8 -> get("music.access.paiddownload");
            default -> get("music.access.paid");
        };
    }
    public static String collection(MusicCollection collection) {
        String description = collection.owner().isBlank() ? get(collection.type().nameKey()) : collection.owner();
        return collection.trackCount() < 0 ? description : description + " · " + get("music.tracks", collection.trackCount());
    }
    public static String artist(MusicTrack track) {
        String access = access(track);
        return access.isEmpty() ? track.artist() : access + (track.artist().isBlank() ? "" : " · " + track.artist());
    }
    public static String downloadAction(MusicTrack track) {
        return track.preview() ? get("music.action.downloadpreview") : get("music.action.download")
            + (access(track).isEmpty() ? "" : " · " + access(track));
    }
    public static String downloaded(MusicTrack track) {
        return track.preview() ? get("music.status.downloadedpreview", track.title(), access(track)) : get("music.status.downloaded", track.title());
    }
    public static String playing(MusicTrack track) {
        return get("music.status.playing", track.title()) + (track.preview() ? " · " + access(track) : "");
    }
}
