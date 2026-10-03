package cn.pupperclient.management.music;

public enum MusicRepeatMode {
    OFF, ALL, ONE;

    public MusicRepeatMode next() { return values()[(ordinal() + 1) % values().length]; }
    public String textKey() { return "music.repeat." + name().toLowerCase(java.util.Locale.ROOT); }
    public static MusicRepeatMode parse(String value) {
        try { return valueOf(value == null ? "OFF" : value.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return OFF; }
    }
}
