package dev.openyourmouth.audio;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public final class TtsPlayback {
    private short[] samples;
    private int position;
    private CompletableFuture<Void> completion;

    public synchronized CompletableFuture<Void> play(short[] audio) {
        stop();
        samples = audio.clone();
        position = 0;
        completion = new CompletableFuture<>();
        if (samples.length == 0) finish();
        return completion;
    }

    public synchronized short[] pollFrame() {
        return pollSamples(PcmAudio.FRAME_SAMPLES);
    }

    public synchronized short[] pollSamples(int requestedSamples) {
        if (requestedSamples <= 0) throw new IllegalArgumentException("requestedSamples must be positive");
        if (samples == null) return null;
        short[] frame = Arrays.copyOfRange(samples, position, Math.min(samples.length, position + requestedSamples));
        if (frame.length < requestedSamples) frame = Arrays.copyOf(frame, requestedSamples);
        position += Math.min(requestedSamples, samples.length - position);
        if (position >= samples.length) finish();
        return frame;
    }

    public synchronized boolean isPlaying() {
        return samples != null;
    }

    public synchronized void stop() {
        if (completion != null && !completion.isDone()) completion.completeExceptionally(new InterruptedException("TTS stopped"));
        samples = null;
        completion = null;
        position = 0;
    }

    private void finish() {
        samples = null;
        position = 0;
        if (completion != null) completion.complete(null);
    }
}
