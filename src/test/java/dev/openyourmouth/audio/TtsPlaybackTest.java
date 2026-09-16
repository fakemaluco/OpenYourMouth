package dev.openyourmouth.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TtsPlaybackTest {
    @Test
    void emitsTwentyMillisecondFramesAndCompletes() {
        TtsPlayback playback = new TtsPlayback();
        short[] samples = new short[PcmAudio.FRAME_SAMPLES + 10];
        var completion = playback.play(samples);
        assertEquals(PcmAudio.FRAME_SAMPLES, playback.pollFrame().length);
        assertFalse(completion.isDone());
        assertEquals(PcmAudio.FRAME_SAMPLES, playback.pollFrame().length);
        assertTrue(completion.isDone());
        assertFalse(playback.isPlaying());
        assertNull(playback.pollFrame());
    }

    @Test
    void advancesByTheCaptureFrameSizeInsteadOfSkippingAudio() {
        TtsPlayback playback = new TtsPlayback();
        short[] samples = new short[1_200];
        for (int index = 0; index < samples.length; index++) samples[index] = (short) index;
        var completion = playback.play(samples);
        assertArrayEquals(java.util.Arrays.copyOfRange(samples, 0, 480), playback.pollSamples(480));
        assertArrayEquals(java.util.Arrays.copyOfRange(samples, 480, 960), playback.pollSamples(480));
        short[] last = playback.pollSamples(480);
        assertArrayEquals(java.util.Arrays.copyOfRange(samples, 960, 1_200), java.util.Arrays.copyOf(last, 240));
        assertTrue(completion.isDone());
    }

    @Test
    void rejectsInvalidCaptureFrameSize() {
        assertThrows(IllegalArgumentException.class, () -> new TtsPlayback().pollSamples(0));
    }
}
