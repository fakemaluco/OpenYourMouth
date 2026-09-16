package dev.openyourmouth.audio;

import dev.openyourmouth.OpenYourMouthClient;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Plays an optional local copy without routing it back through the voice-chat capture stream. */
public final class LocalTtsMonitor implements AutoCloseable {
    private static final AudioFormat FORMAT = new AudioFormat(PcmAudio.SAMPLE_RATE, 16, 1, true, false);
    private final ExecutorService output = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "OpenYourMouth-HearSelf");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong generation = new AtomicLong();
    private final Object lineLock = new Object();
    private SourceDataLine activeLine;
    private volatile boolean closed;

    public void play(short[] samples, double volume) {
        if (closed || samples == null || samples.length == 0) return;
        long token = generation.incrementAndGet();
        closeActiveLine();
        byte[] pcm = pcmBytes(samples, volume);
        output.submit(() -> playNow(pcm, token));
    }

    public void stop() {
        generation.incrementAndGet();
        closeActiveLine();
    }

    private void playNow(byte[] pcm, long token) {
        SourceDataLine line = null;
        try {
            if (closed || token != generation.get()) return;
            line = AudioSystem.getSourceDataLine(FORMAT);
            line.open(FORMAT, PcmAudio.FRAME_SAMPLES * 8);
            synchronized (lineLock) {
                if (closed || token != generation.get()) return;
                activeLine = line;
            }
            line.start();
            int offset = 0;
            int chunkBytes = PcmAudio.FRAME_SAMPLES * 2 * 4;
            while (offset < pcm.length && token == generation.get() && !closed) {
                int length = Math.min(chunkBytes, pcm.length - offset);
                int written = line.write(pcm, offset, length);
                if (written <= 0) break;
                offset += written;
            }
            if (token == generation.get() && !closed) line.drain();
        } catch (Exception exception) {
            if (token == generation.get() && !closed) {
                OpenYourMouthClient.LOGGER.warn("Hear-self playback failed: {}", exception.toString());
            }
        } finally {
            synchronized (lineLock) {
                if (activeLine == line) activeLine = null;
            }
            if (line != null) {
                line.stop();
                line.close();
            }
        }
    }

    static byte[] pcmBytes(short[] samples, double volume) {
        double gain = Math.max(0.0D, Math.min(2.0D, volume));
        byte[] bytes = new byte[samples.length * 2];
        for (int index = 0; index < samples.length; index++) {
            int scaled = (int) Math.round(samples[index] * gain);
            short value = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, scaled));
            bytes[index * 2] = (byte) (value & 0xFF);
            bytes[index * 2 + 1] = (byte) ((value >>> 8) & 0xFF);
        }
        return bytes;
    }

    private void closeActiveLine() {
        synchronized (lineLock) {
            if (activeLine == null) return;
            activeLine.stop();
            activeLine.flush();
            activeLine.close();
            activeLine = null;
        }
    }

    @Override
    public void close() {
        closed = true;
        stop();
        output.shutdownNow();
    }
}
