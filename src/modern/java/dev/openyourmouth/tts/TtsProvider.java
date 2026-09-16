package dev.openyourmouth.tts;

import dev.openyourmouth.config.TtsConfig;

public interface TtsProvider {
    short[] synthesize(String text, TtsConfig config) throws Exception;
}
