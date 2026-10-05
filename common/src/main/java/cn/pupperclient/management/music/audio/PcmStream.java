package cn.pupperclient.management.music.audio;

import java.util.Arrays;
import javax.sound.sampled.AudioFormat;

/** Bounded streaming linear resampling/channel conversion, retaining phase across codec frames. */
public final class PcmStream implements AutoCloseable {
    private final PcmDecoder decoder;
    private final int sourceChannels, outputChannels;
    private final long inputRate, outputRate;
    private short[] source;
    private int sourceAt;
    private long phase;
    private short[] first, second;
    private boolean initialized;

    public PcmStream(PcmDecoder decoder, short[] prefix, AudioFormat output) {
        this.decoder = decoder; sourceChannels = decoder.format().getChannels(); outputChannels = output.getChannels();
        inputRate = rate(decoder.format()); outputRate = rate(output);
        if (sourceChannels < 1 || outputChannels < 1) throw new IllegalArgumentException("Invalid PCM channels");
        source = prefix == null ? new short[0] : prefix;
    }
    public PcmDecoder decoder() { return decoder; }
    public short[] read(int frames) throws Exception {
        if (frames < 1) return new short[0];
        if (!initialized) { first = frame(); second = frame(); initialized = true; }
        short[] output = new short[frames * outputChannels];
        int count = 0;
        while (count < frames && first != null) {
            for (int c = 0; c < outputChannels; c++) {
                double a = channel(first, c), b = second == null ? a : channel(second, c);
                output[count * outputChannels + c] = (short) Math.round(a + (b - a) * (phase / (double) outputRate));
            }
            count++; phase += inputRate;
            while (phase >= outputRate && first != null) { phase -= outputRate; first = second; second = first == null ? null : frame(); }
        }
        return count == 0 ? null : Arrays.copyOf(output, count * outputChannels);
    }
    private static long rate(AudioFormat format) {
        float rate = format.getSampleRate(); long rounded = Math.round(rate);
        if (!Float.isFinite(rate) || rounded < 1 || rate != rounded) throw new IllegalArgumentException("Unsupported fractional PCM sample rate");
        return rounded;
    }
    private double channel(short[] frame, int output) {
        if (sourceChannels == 1) return frame[0];
        if (outputChannels == 1) { double sum = 0; for (short sample : frame) sum += sample; return sum / sourceChannels; }
        if (sourceChannels == outputChannels) return frame[output];
        // Stereo preserves left/right; additional channels contribute quietly to both, avoiding hard clipping.
        double sum = frame[Math.min(output, sourceChannels - 1)], weight = 1;
        for (int c = 2; c < sourceChannels; c++) { sum += frame[c] * .5; weight += .5; }
        return sum / weight;
    }
    private short[] frame() throws Exception {
        if (source == null) return null;
        while (sourceAt + sourceChannels > source.length) {
            source = decoder.read(); sourceAt = 0;
            if (source == null) return null;
        }
        short[] frame = Arrays.copyOfRange(source, sourceAt, sourceAt + sourceChannels); sourceAt += sourceChannels;
        return frame;
    }
    @Override public void close() throws Exception { decoder.close(); }
}
