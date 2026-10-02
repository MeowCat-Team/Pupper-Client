package cn.pupperclient.management.music.lyric;

public class LyricLine {
    private final long millis;
    private final String text;
    private final String translation;

    public LyricLine(float time, String text) {
        this(Math.round(time * 1000d), text, "");
    }

    public LyricLine(long millis, String text, String translation) {
        this.millis = millis;
        this.text = text;
        this.translation = translation;
    }

    public float getTime() {
        return millis / 1000f;
    }

    public long getMillis() { return millis; }
    public String getTranslation() { return translation; }

    public String getText() {
        return text;
    }
}
