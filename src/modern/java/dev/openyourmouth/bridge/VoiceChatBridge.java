package dev.openyourmouth.bridge;

import dev.openyourmouth.config.TtsConfig;

public interface VoiceChatBridge {
    TtsConfig.Backend backend();
    boolean isConnected();
}
