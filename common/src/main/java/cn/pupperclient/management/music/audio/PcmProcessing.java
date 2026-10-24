/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cn.pupperclient.management.music.audio;

/** PCM head RMS/peak leveling, explicitly not EBU R128 or whole-track loudness measurement. */
public final class PcmProcessing {
    private PcmProcessing() { }
    public static double levelGain(short[] samples) {
        if (samples.length == 0) return 1;
        double squares = 0, peak = 0;
        for (short sample : samples) { double value = sample / 32768d; squares += value * value; peak = Math.max(peak, Math.abs(value)); }
        if (peak < .00001) return 1;
        double rms = Math.sqrt(squares / samples.length);
        return Math.min(.98 / peak, Math.clamp(.125892541 / Math.max(.00001, rms), .125, 4));
    }
    public static short limit(double value) { return (short) Math.clamp(Math.round(value), -32768, 32767); }
    public static void gain(short[] samples, double gain) {
        if (gain == 1) return;
        for (int i = 0; i < samples.length; i++) samples[i] = limit(samples[i] * gain);
    }
    public static void fadeIn(short[] samples, int channels, long firstFrame, int fadeFrames) {
        if (fadeFrames < 1) return;
        for (int f = 0; f < samples.length / channels && firstFrame + f < fadeFrames; f++) {
            double gain = (firstFrame + f) / (double) Math.max(1, fadeFrames - 1);
            for (int c = 0; c < channels; c++) samples[f * channels + c] = limit(samples[f * channels + c] * gain);
        }
    }
    public static short[] crossfade(short[] outgoing, short[] incoming, int channels) {
        if (outgoing.length != incoming.length || outgoing.length % channels != 0) throw new IllegalArgumentException("Mismatched PCM overlap");
        short[] mixed = new short[outgoing.length];
        int frames = outgoing.length / channels;
        for (int f = 0; f < frames; f++) {
            double amount = frames == 1 ? 1 : f / (double) (frames - 1);
            for (int c = 0; c < channels; c++) {
                int i = f * channels + c;
                mixed[i] = limit(outgoing[i] * (1 - amount) + incoming[i] * amount);
            }
        }
        return mixed;
    }
    public static byte[] bytes(short[] samples) {
        byte[] bytes = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) { bytes[i * 2] = (byte) samples[i]; bytes[i * 2 + 1] = (byte) (samples[i] >> 8); }
        return bytes;
    }
}
