package cn.pupperclient.management.music.audio;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/** Reads Xing/Info encoder counts; malformed or absent metadata never invents trim timestamps. */
public final class Mp3Gapless {
    public record Info(boolean metadataFrame, long skipFrames, long playableFrames) {
        public static final Info NONE = new Info(false, 0, -1);
    }
    private Mp3Gapless() { }
    public static Info read(File file, int frameOffset) {
        if (frameOffset < 0) return Info.NONE;
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            input.seek(frameOffset);
            byte[] bytes = new byte[(int) Math.min(2048, input.length() - frameOffset)]; input.readFully(bytes);
            if (bytes.length < 48) return Info.NONE;
            int header = integer(bytes, 0), version = header >>> 19 & 3;
            if ((header & 0xffe00000) != 0xffe00000 || version == 1 || (header >>> 17 & 3) != 1) return Info.NONE;
            boolean mono = (header >>> 6 & 3) == 3;
            int at = 4 + (version == 3 ? mono ? 17 : 32 : mono ? 9 : 17);
            if ((header >>> 16 & 1) == 0) at += 2;
            if (!text(bytes, at, "Xing") && !text(bytes, at, "Info")) return Info.NONE;
            int flags = integer(bytes, at + 4); at += 8;
            long frames = 0;
            if ((flags & 1) != 0) { frames = Integer.toUnsignedLong(integer(bytes, at)); at += 4; }
            if ((flags & 2) != 0) at += 4;
            if ((flags & 4) != 0) at += 100;
            if ((flags & 8) != 0) at += 4;
            if (at + 24 > bytes.length || !(text(bytes, at, "LAME") || text(bytes, at, "Lavc") || text(bytes, at, "Lavf")))
                return new Info(true, 0, -1);
            int delay = (bytes[at + 21] & 255) << 4 | (bytes[at + 22] & 255) >>> 4;
            int padding = (bytes[at + 22] & 15) << 8 | bytes[at + 23] & 255;
            // The Layer III synthesis decoder contributes 528+1 samples; the Info frame itself is never audio.
            // This matches the encoder-count interpretation in FFmpeg's official mp3dec.c.
            long full = frames * (version == 3 ? 1152L : 576L), playable = full - delay - padding;
            long skip = delay + 529L;
            if (frames == 0 || padding < 529 || playable < 1 || playable > full - skip) return new Info(true, 0, -1);
            return new Info(true, skip, playable);
        } catch (Exception malformed) { return Info.NONE; }
    }
    private static int integer(byte[] bytes, int at) {
        if (at < 0 || at + 4 > bytes.length) throw new IllegalArgumentException("Truncated MPEG metadata");
        return (bytes[at] & 255) << 24 | (bytes[at + 1] & 255) << 16 | (bytes[at + 2] & 255) << 8 | bytes[at + 3] & 255;
    }
    private static boolean text(byte[] bytes, int at, String text) {
        return at >= 0 && at + text.length() <= bytes.length
            && new String(bytes, at, text.length(), StandardCharsets.ISO_8859_1).equals(text);
    }
}
