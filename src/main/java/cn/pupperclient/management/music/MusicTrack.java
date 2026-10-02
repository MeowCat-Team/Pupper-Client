package cn.pupperclient.management.music;

/** Provider metadata is independent of filenames and embedded audio tags. */
public record MusicTrack(long id, String title, String artist, String album, String coverUrl, long durationMillis) {
    public MusicTrack {
        title = title == null ? "" : title;
        artist = artist == null ? "" : artist;
        album = album == null ? "" : album;
        coverUrl = coverUrl == null ? "" : coverUrl;
        durationMillis = Math.max(0, durationMillis);
    }
}
