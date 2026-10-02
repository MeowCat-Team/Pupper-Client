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
}
