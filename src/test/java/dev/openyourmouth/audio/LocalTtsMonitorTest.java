package dev.openyourmouth.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class LocalTtsMonitorTest {
    @Test
    void encodesLittleEndianAndAppliesSaturatedVolume() {
        short[] samples = {0x1234, 20_000, -20_000};
        assertArrayEquals(new byte[] {0x68, 0x24, (byte) 0xFF, 0x7F, 0x00, (byte) 0x80},
                LocalTtsMonitor.pcmBytes(samples, 2.0D));
    }
}
