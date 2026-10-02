package cn.pupperclient.management.music;

/** Provider metadata is independent of filenames and embedded audio tags. */
public record MusicTrack(long id, String title, String artist, String album, String coverUrl, long durationMillis,
        String provider, String providerId, boolean playable, boolean downloadable) {
    /** Keeps legacy NetEase indexes and local audio metadata readable. */
    public MusicTrack(long id, String title, String artist, String album, String coverUrl, long durationMillis) {
        this(id, title, artist, album, coverUrl, durationMillis, null, null, true, true);
    }

    public MusicTrack {
        if (provider == null || provider.isBlank()) {
            provider = id > 0 ? "netease" : "local";
            playable = true;
            downloadable = true;
        }
        providerId = providerId == null || providerId.isBlank() ? (id > 0 ? Long.toString(id) : "") : providerId;
        title = title == null ? "" : title;
        artist = artist == null ? "" : artist;
        album = album == null ? "" : album;
        coverUrl = coverUrl == null ? "" : coverUrl;
        durationMillis = Math.max(0, durationMillis);
    }

    public boolean remote() { return !provider.equals("local") && !providerId.isBlank(); }
    public String key() { return provider + ":" + providerId; }
    public boolean sameSong(MusicTrack other) { return remote() && other.remote() && key().equals(other.key()); }
    /** Hex preserves case-sensitive provider IDs on case-insensitive Windows filesystems. */
    public String cacheId() {
        return provider + "-" + java.util.HexFormat.of().formatHex(providerId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public String coverFilename() { return provider.equals("netease") ? "ncm-cover-" + id + ".jpg" : cacheId() + "-cover.jpg"; }
    public String lyricsFilename() { return provider.equals("netease") ? "ncm-lyrics-" + id + ".json" : cacheId() + "-lyrics.json"; }
}
