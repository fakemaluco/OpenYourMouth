package dev.openyourmouth.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PcmAudio {
    public static final int SAMPLE_RATE = 48_000;
    public static final int FRAME_SAMPLES = 960;

    private PcmAudio() {}

    public static short saturatingAdd(short first, short second) {
        int value = first + second;
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }

    public static void mixInto(short[] target, short[] addition, double gain) {
        mixInto(target, addition, gain, 1.0D);
    }

    public static void mixInto(short[] target, short[] addition, double gain, double existingAudioGain) {
        int length = Math.min(target.length, addition.length);
        for (int i = 0; i < length; i++) {
            int existing = (int) Math.round(target[i] * existingAudioGain);
            int scaled = (int) Math.round(addition[i] * gain);
            scaled = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, scaled));
            existing = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, existing));
            target[i] = saturatingAdd((short) existing, (short) scaled);
        }
    }

    public static short[] resample(short[] input, int sourceRate, int targetRate) {
        if (sourceRate == targetRate || input.length == 0) return input.clone();
        int outputLength = Math.max(1, (int) Math.round(input.length * (targetRate / (double) sourceRate)));
        short[] output = new short[outputLength];
        double ratio = sourceRate / (double) targetRate;
        for (int i = 0; i < outputLength; i++) {
            double source = i * ratio;
            int left = Math.min(input.length - 1, (int) source);
            int right = Math.min(input.length - 1, left + 1);
            double fraction = source - left;
            output[i] = (short) Math.round(input[left] * (1.0D - fraction) + input[right] * fraction);
        }
        return output;
    }

    public static short[] decodeLinearWav(byte[] bytes) throws Exception {
        try (AudioInputStream original = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, original)) {
                return littleEndianToShorts(readAll(pcm));
            }
        }
    }

    public static short[] littleEndianToShorts(byte[] bytes) {
        short[] result = new short[bytes.length / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (short) ((bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] << 8));
        }
        return result;
    }

    private static byte[] readAll(AudioInputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        stream.transferTo(output);
        return output.toByteArray();
    }
}
