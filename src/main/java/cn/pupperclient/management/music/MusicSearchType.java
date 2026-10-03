package cn.pupperclient.management.music;

/** Search categories shared by providers and the player. */
public enum MusicSearchType {
    SONGS, ARTISTS, PLAYLISTS;
    public String nameKey() { return "music.search." + name().toLowerCase(java.util.Locale.ROOT); }
}
