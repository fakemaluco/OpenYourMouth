package dev.openyourmouth.tts;

import dev.openyourmouth.audio.PcmAudio;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class Mp3Decoder {
    private Mp3Decoder() {}

    public static short[] decode(byte[] mp3) throws Exception {
        Bitstream bitstream = new Bitstream(new ByteArrayInputStream(mp3));
        Decoder decoder = new Decoder();
        ByteArrayOutputStream mono = new ByteArrayOutputStream();
        int sampleRate = 24_000;
        Header header;
        while ((header = bitstream.readFrame()) != null) {
            SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            sampleRate = buffer.getSampleFrequency();
            short[] values = buffer.getBuffer();
            int length = buffer.getBufferLength();
            int channels = buffer.getChannelCount();
            if (channels == 1) {
                for (int i = 0; i < length; i++) writeShort(mono, values[i]);
            } else {
                for (int i = 0; i + 1 < length; i += channels) {
                    int mixed = 0;
                    for (int channel = 0; channel < channels; channel++) mixed += values[i + channel];
                    writeShort(mono, (short) (mixed / channels));
                }
            }
            bitstream.closeFrame();
        }
        bitstream.close();
        if (mono.size() == 0) throw new IllegalArgumentException("TTS provider returned no MP3 samples");
        return PcmAudio.resample(PcmAudio.littleEndianToShorts(mono.toByteArray()), sampleRate, PcmAudio.SAMPLE_RATE);
    }

    private static void writeShort(ByteArrayOutputStream output, short value) {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
    }
}
