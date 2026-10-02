package cn.pupperclient.management.music;

/** UI errors carry translation keys, never authenticated request URLs or cookies. */
public class MusicError extends Exception {
    private final String key;

    public MusicError(String key) {
        super(key);
        this.key = key;
    }

    public String key() { return key; }
}
