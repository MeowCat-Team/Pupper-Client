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

/** Provider and quality resolution for requests from commands or other music clients. */
public final class MusicRequest {
    public enum Action { PLAY, DOWNLOAD }
    public record Target(MusicProvider provider, String trackId, String quality) { }
    public record Result(Action action, Music music) { }
    private MusicRequest() { }

    public static Target target(MusicProviders providers, String reference, String requestedQuality) throws MusicError {
        if (reference == null || !reference.matches("(?:[a-zA-Z]+:)?[a-zA-Z0-9_-]{1,80}"))
            throw new MusicError("music.error.metadata");
        MusicProvider provider = providers.forReference(reference);
        String quality = requestedQuality == null ? provider.defaultQuality() : null;
        if (requestedQuality != null) for (String choice : provider.qualities()) {
            if (choice.equalsIgnoreCase(requestedQuality) || MusicText.get("music.quality." + choice).equals(requestedQuality)) {
                quality = choice; break;
            }
        }
        if (quality == null) throw new MusicError("music.error.quality");
        int colon = reference.indexOf(':');
        return new Target(provider, colon < 0 ? reference : reference.substring(colon + 1), quality);
    }
    /** Keep the existing .music quick policy in the music layer, alongside provider routing. */
    public static Action quickAction(MusicTrack track) {
        return track.provider().equals("netease") ? Action.DOWNLOAD : Action.PLAY;
    }
}
