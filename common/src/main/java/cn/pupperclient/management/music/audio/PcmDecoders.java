package cn.pupperclient.management.music.audio;

import cn.pupperclient.libraries.flac.FLACDecoder;
import cn.pupperclient.libraries.flac.metadata.StreamInfo;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/** Real MP3/FLAC decoding, with Java Sound PCM files available for local audio and fixtures. */
public final class PcmDecoders {
    private PcmDecoders() { }
    public static PcmDecoder open(File file) throws Exception {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".mp3") ? new Mp3(file) : name.endsWith(".flac") ? new Flac(file) : new Pcm(file);
    }
    private static AudioFormat format(int rate, int channels) throws IOException {
        if (rate < 8000 || rate > 384000 || channels < 1 || channels > 8) throw new IOException("Unsupported PCM format");
        return new AudioFormat(rate, 16, channels, true, false);
    }

    private static final class Mp3 implements PcmDecoder {
        private final BufferedInputStream input;
        private final Bitstream stream;
        private final Decoder decoder = new Decoder();
        private final AudioFormat format;
        private final double duration;
        private Header header;
        private long skipFrames, remainingFrames = Long.MAX_VALUE;
        Mp3(File file) throws Exception {
            input = new BufferedInputStream(new FileInputStream(file)); stream = new Bitstream(input);
            try {
                header = stream.readFrame();
                if (header == null) throw new IOException("Empty MP3");
                format = PcmDecoders.format(header.frequency(), header.mode() == Header.SINGLE_CHANNEL ? 1 : 2);
                double seconds = 0;
                try {
                    var metadata = new com.mpatric.mp3agic.Mp3File(file);
                    seconds = metadata.getLengthInMilliseconds() / 1000d;
                    var gapless = Mp3Gapless.read(file, metadata.getXingOffset());
                    if (gapless.metadataFrame()) { stream.closeFrame(); header = stream.readFrame(); }
                    if (gapless.playableFrames() > 0) {
                        skipFrames = gapless.skipFrames(); remainingFrames = gapless.playableFrames();
                        seconds = remainingFrames / (double) format.getSampleRate();
                    }
                }
                catch (Exception unavailable) { /* Unknown duration still permits real sequential decoding. */ }
                duration = seconds;
            } catch (Exception error) { input.close(); throw error; }
        }
        public AudioFormat format() { return format; }
        public double duration() { return duration; }
        public short[] read() throws Exception {
            if (header == null || remainingFrames == 0) return null;
            if (header.frequency() != (int) format.getSampleRate()
                    || (header.mode() == Header.SINGLE_CHANNEL ? 1 : 2) != format.getChannels())
                throw new IOException("MP3 changes format within a stream");
            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, stream);
            short[] samples = output == null ? new short[0] : Arrays.copyOf(output.getBuffer(), output.getBufferLength());
            stream.closeFrame(); header = stream.readFrame();
            int frames = samples.length / format.getChannels(), skipped = (int) Math.min(frames, skipFrames);
            skipFrames -= skipped;
            int delivered = (int) Math.min(frames - skipped, remainingFrames);
            remainingFrames -= delivered;
            return Arrays.copyOfRange(samples, skipped * format.getChannels(), (skipped + delivered) * format.getChannels());
        }
        public void close() throws Exception { try { stream.close(); } finally { input.close(); } }
    }

    private static final class Flac implements PcmDecoder {
        private final FileInputStream input;
        private final FLACDecoder decoder;
        private final StreamInfo metadata;
        private final AudioFormat format;
        Flac(File file) throws Exception {
            input = new FileInputStream(file); decoder = new FLACDecoder(input);
            try {
                metadata = decoder.readStreamInfo(); decoder.readMetadata(metadata);
                format = PcmDecoders.format(metadata.getSampleRate(), metadata.getChannels());
            } catch (Exception error) { input.close(); throw error; }
        }
        public AudioFormat format() { return format; }
        public double duration() { return metadata.getTotalSamples() / (double) metadata.getSampleRate(); }
        public short[] read() throws Exception {
            var frame = decoder.readNextFrame();
            if (frame == null) return null;
            int channels = format.getChannels(), bits = metadata.getBitsPerSample();
            if (frame.header.sampleRate != metadata.getSampleRate() || frame.header.channels != channels
                    || frame.header.bitsPerSample != bits)
                throw new IOException("FLAC changes format within a stream");
            short[] samples = new short[frame.header.blockSize * channels];
            var decoded = decoder.getChannelData();
            for (int f = 0; f < frame.header.blockSize; f++) for (int c = 0; c < channels; c++) {
                int sample = decoded[c].getOutput()[f];
                samples[f * channels + c] = (short) (bits > 16 ? sample >> (bits - 16) : sample << (16 - bits));
            }
            return samples;
        }
        public void close() throws IOException { input.close(); }
    }

    private static final class Pcm implements PcmDecoder {
        private final AudioInputStream input;
        private final AudioFormat format;
        private final double duration;
        Pcm(File file) throws Exception {
            AudioInputStream raw = AudioSystem.getAudioInputStream(file);
            AudioFormat source = raw.getFormat();
            format = PcmDecoders.format(Math.round(source.getSampleRate()), source.getChannels());
            duration = raw.getFrameLength() > 0 ? raw.getFrameLength() / source.getFrameRate() : 0;
            try { input = AudioSystem.getAudioInputStream(format, raw); }
            catch (RuntimeException error) { raw.close(); throw error; }
        }
        public AudioFormat format() { return format; }
        public double duration() { return duration; }
        public short[] read() throws IOException {
            byte[] bytes = input.readNBytes(4096 * format.getFrameSize());
            if (bytes.length == 0) return null;
            if (bytes.length % format.getFrameSize() != 0) throw new IOException("Incomplete PCM frame");
            short[] samples = new short[bytes.length / 2];
            for (int i = 0; i < samples.length; i++) samples[i] = (short) ((bytes[i * 2] & 255) | bytes[i * 2 + 1] << 8);
            return samples;
        }
        public void close() throws IOException { input.close(); }
    }
}
