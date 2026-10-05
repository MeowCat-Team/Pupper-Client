package cn.pupperclient.management.music.audio;

import javax.sound.sampled.AudioFormat;

/** Decoder instances belong to exactly one worker at a time; samples are signed, interleaved PCM16. */
public interface PcmDecoder extends AutoCloseable {
    AudioFormat format();
    double duration();
    short[] read() throws Exception;
    @Override void close() throws Exception;
}
