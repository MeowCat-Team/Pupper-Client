package cn.pupperclient.management.music;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.libraries.flac.FLACDecoder;
import cn.pupperclient.libraries.flac.frame.Frame;
import cn.pupperclient.libraries.flac.metadata.StreamInfo;
import cn.pupperclient.libraries.flac.util.ByteData;

public class MusicPlayer implements Runnable {

    public static final int SPECTRUM_BANDS = 100;
    public static float[] VISUALIZER = new float[SPECTRUM_BANDS];
    public static SimpleAnimation[] ANIMATIONS = new SimpleAnimation[SPECTRUM_BANDS];

    static {
        for (int i = 0; i < SPECTRUM_BANDS; i++) {
            VISUALIZER[i] = 0.0F;
            ANIMATIONS[i] = new SimpleAnimation();
        }
    }

    private static final int FFT_SIZE = 1024;
    private float[] fftBuffer = new float[FFT_SIZE];
    private float[] magnitudes = new float[SPECTRUM_BANDS];
    private byte[] mp3Buffer = new byte[8192]; // Reuse buffer for MP3

    private Runnable runnable;

    // FLAC
    private FLACDecoder decoder;
    private volatile StreamInfo streamInfo;

    // MP3
    private Bitstream bitstream;
    private Decoder mp3Decoder;
    private Header mp3Header;

    private volatile AudioFormat audioFormat;
    private volatile SourceDataLine sourceDataLine;

    private volatile Music currentMusic;
    private volatile boolean playing;
    private volatile boolean repeat;
    private volatile float volume;
    private volatile long generation;

    private volatile float lastCurrentTime;
    private volatile float mp3Duration = 0;

    public MusicPlayer(Runnable runnable) {
        this.runnable = runnable;
        this.playing = false;
        this.volume = 0.5F;
    }

    @Override
    public void run() {
        long session = generation;
        Music track = currentMusic;
        if (track != null && playing) {
            String fileName = track.getAudio().getName().toLowerCase(java.util.Locale.ROOT);

            if (fileName.endsWith(".flac")) {
                playFlacFile(track, session);
            } else if (fileName.endsWith(".mp3")) {
                playMp3File(track, session);
            }
        }
    }

    private void playFlacFile(Music track, long session) {
        if (track == null || !playing || session != generation || track != currentMusic) return;
        SourceDataLine line = null;
        try (FileInputStream fis = new FileInputStream(track.getAudio())) {
            decoder = new FLACDecoder(fis);
            streamInfo = decoder.readStreamInfo();
            audioFormat = new AudioFormat(streamInfo.getSampleRate(),
                streamInfo.getBitsPerSample() == 24 ? 16 : streamInfo.getBitsPerSample(),
                streamInfo.getChannels(), (streamInfo.getBitsPerSample() <= 8) ? false : true, false);
            line = createLine(audioFormat);
            line.open(audioFormat);
            synchronized (this) {
                if (session != generation || Thread.currentThread().isInterrupted()) return;
                sourceDataLine = line;
                setVolume(volume);
                line.start();
            }

            Frame frame;
            ByteData byteData = new ByteData(FFT_SIZE * 4);

            while (session == generation && !Thread.currentThread().isInterrupted()
                    && (frame = decoder.readNextFrame()) != null) {

                while (!playing && session == generation) {
                    Thread.sleep(10);
                }
                if (session != generation || Thread.currentThread().isInterrupted()) return;

                ByteData pcm = decoder.decodeFrame(frame, byteData);
                updateSpectrum(pcm.getData(), pcm.getLen());
                line.write(pcm.getData(), 0, pcm.getLen());
            }

            if (session == generation && !Thread.currentThread().isInterrupted()) {
                line.drain();
                complete(session);
            }
        } catch (Exception e) {
            if (session == generation && !(e instanceof InterruptedException)) {
                playing = false;
                cn.pupperclient.PupperLogger.error("MusicPlayer", "Failed to play FLAC file", e);
            }
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            releaseLine(line);
        }
    }

    private void playMp3File(Music track, long session) {
        if (track == null || !playing || session != generation || track != currentMusic) return;
        SourceDataLine line = null;
        try (FileInputStream fis = new FileInputStream(track.getAudio());
             BufferedInputStream bis = new BufferedInputStream(fis)) {

            calculateMp3Duration(track.getAudio());

            bitstream = new Bitstream(bis);
            mp3Decoder = new Decoder();

            mp3Header = bitstream.readFrame();
            if (mp3Header == null) { playing = false; return; }

            int sampleRate = mp3Header.frequency();
            int channels = mp3Header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;

            audioFormat = new AudioFormat(sampleRate, 16, channels, true, false);
            line = createLine(audioFormat);
            line.open(audioFormat);
            synchronized (this) {
                if (session != generation || Thread.currentThread().isInterrupted()) return;
                sourceDataLine = line;
                setVolume(volume);
                line.start();
            }

            do {
                while (!playing && session == generation) {
                    Thread.sleep(10);
                }
                if (session != generation || Thread.currentThread().isInterrupted()) return;

                SampleBuffer output = (SampleBuffer) mp3Decoder.decodeFrame(mp3Header, bitstream);
                if (output != null) {
                    int length = output.getBufferLength();
                    ensureMp3Buffer(length * 2);
                    fillMp3Buffer(output.getBuffer(), length);
                    updateSpectrum(mp3Buffer, length * 2);
                    line.write(mp3Buffer, 0, length * 2);
                }

                bitstream.closeFrame();
                mp3Header = bitstream.readFrame();

            } while (mp3Header != null && session == generation);

            if (mp3Header == null && session == generation) {
                line.drain();
                complete(session);
            }
            bitstream.close();

        } catch (Exception e) {
            if (session == generation && !(e instanceof InterruptedException)) {
                playing = false;
                cn.pupperclient.PupperLogger.error("MusicPlayer", "Failed to play MP3 file", e);
            }
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            releaseLine(line);
        }
    }

    private void ensureMp3Buffer(int length) {
        if (mp3Buffer.length < length) {
            mp3Buffer = new byte[length];
        }
    }

    private void fillMp3Buffer(short[] samples, int length) {
        for (int i = 0; i < length; i++) {
            mp3Buffer[i * 2] = (byte) (samples[i] & 0xFF);
            mp3Buffer[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
    }

    private void calculateMp3Duration(File mp3File) {
        try {
            com.mpatric.mp3agic.Mp3File mp3 = new com.mpatric.mp3agic.Mp3File(mp3File);
            mp3Duration = (float) mp3.getLengthInSeconds();
        } catch (Exception e) {
            try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(mp3File)) {
                AudioFormat format = audioInputStream.getFormat();
                long frames = audioInputStream.getFrameLength();
                mp3Duration = (float) (frames / format.getFrameRate());
            } catch (Exception ex) {
                mp3Duration = 0;
                cn.pupperclient.PupperLogger.error("MusicPlayer", "Failed to calculate MP3 duration", ex);
            }
        }
    }

    private void updateSpectrum(byte[] audioData, int length) {

        int samples = Math.min(length / 2, FFT_SIZE);
        for (int i = 0; i < samples; i++) {
            int index = i * 2;
            short sample = (short) ((audioData[index + 1] << 8) | (audioData[index] & 0xFF));
            fftBuffer[i] = sample / 32768.0f;
        }

        for (int i = 0; i < SPECTRUM_BANDS; i++) {
            float sum = 0;
            int startIdx = (i * FFT_SIZE) / SPECTRUM_BANDS;
            int endIdx = ((i + 1) * FFT_SIZE) / SPECTRUM_BANDS;

            for (int j = startIdx; j < endIdx; j++) {
                sum += Math.abs(fftBuffer[j]);
            }

            float average = sum / (endIdx - startIdx);
            magnitudes[i] = average * 60;
            VISUALIZER[i] = magnitudes[i] * (-2F);
        }
    }

    public synchronized void setCurrentMusic(Music currentMusic) {

        playing = false;
        generation++;

        stopCurrentLine();

        this.currentMusic = currentMusic;
        lastCurrentTime = 0;
        mp3Duration = 0;
        streamInfo = null;
        playing = currentMusic != null;
    }

    private synchronized void complete(long session) {
        if (session != generation || Thread.currentThread().isInterrupted()) return;
        if (repeat) {
            // Reopen the decoder on the next worker iteration after this session's line has closed.
            // Keep the current pause state, and never depend on a queued UI callback to restart a loop.
            generation++;
            lastCurrentTime = 0;
            mp3Duration = 0;
            streamInfo = null;
        } else {
            playing = false;
            runnable.run();
        }
    }

    public void setRepeat(boolean repeat) { this.repeat = repeat; }

    protected SourceDataLine createLine(AudioFormat format) throws javax.sound.sampled.LineUnavailableException {
        return (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
    }

    public long getGeneration() { return generation; }

    public synchronized void shutdown() {
        generation++;
        playing = false;
        stopCurrentLine();
    }

    // Called while holding this player's lock; detach before shutdown races with decoder cleanup.
    private void stopCurrentLine() {
        SourceDataLine line = sourceDataLine;
        sourceDataLine = null;
        if (line != null) { line.stop(); line.flush(); line.close(); }
    }

    private void releaseLine(SourceDataLine line) {
        if (line == null) return;
        synchronized (this) {
            // An old session must never clear a newer session's published output.
            if (sourceDataLine == line) sourceDataLine = null;
        }
        line.close();
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public float getCurrentTime() {
        SourceDataLine line = sourceDataLine;
        if (line == null || audioFormat == null) {
            return 0;
        }

        if (!playing) {
            return lastCurrentTime;
        }

        lastCurrentTime = (float) (line.getMicrosecondPosition() / 1000000.0);
        return lastCurrentTime;
    }

    public float getEndTime() {
        Music track = currentMusic;
        String fileName = track != null ? track.getAudio().getName().toLowerCase(java.util.Locale.ROOT) : "";

        if (fileName.endsWith(".flac")) {
            StreamInfo metadata = streamInfo;
            if (metadata == null) {
                return 0;
            }

            long totalSamples = metadata.getTotalSamples();
            int sampleRate = metadata.getSampleRate();

            if (totalSamples > 0 && sampleRate > 0) {
                return (float) totalSamples / sampleRate;
            }
        } else if (fileName.endsWith(".mp3")) {
            return mp3Duration;
        }

        return 0;
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        if (volume >= 0.0f && volume <= 1.0f) {
            this.volume = volume;
            SourceDataLine line = sourceDataLine;
            if (line != null) {
                try {
                    FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    float gain = volume <= 0 ? gainControl.getMinimum() : (float) (Math.log10(volume) * 20.0);
                    gainControl.setValue(Math.clamp(gain, gainControl.getMinimum(), gainControl.getMaximum()));
                } catch (Exception e) {
                }
            }
        }
    }
}
