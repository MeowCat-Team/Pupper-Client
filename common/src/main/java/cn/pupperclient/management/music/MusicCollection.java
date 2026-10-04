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
