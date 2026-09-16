package dev.openyourmouth.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PcmAudioTest {
    @Test
    void saturatingMixDoesNotWrap() {
        short[] target = {30_000, -30_000, 100};
        short[] addition = {10_000, -10_000, 200};
        PcmAudio.mixInto(target, addition, 1.0D);
        assertArrayEquals(new short[]{Short.MAX_VALUE, Short.MIN_VALUE, 300}, target);
    }

    @Test
    void resamplesToRequestedLength() {
        short[] input = new short[24_000];
        short[] output = PcmAudio.resample(input, 24_000, 48_000);
        assertEquals(48_000, output.length);
    }

    @Test
    void convertsLittleEndianPcm() {
        assertArrayEquals(new short[]{1, -2}, PcmAudio.littleEndianToShorts(new byte[]{1, 0, -2, -1}));
    }

    @Test
    void ducksExistingMicrophoneAudioBeforeMixing() {
        short[] microphone = {20_000};
        PcmAudio.mixInto(microphone, new short[]{10_000}, 1.0D, 0.5D);
        assertArrayEquals(new short[]{20_000}, microphone);
    }
}
