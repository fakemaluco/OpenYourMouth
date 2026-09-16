package dev.openyourmouth.tts;

import dev.openyourmouth.config.TtsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeTtsLiveTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "OPENYOURMOUTH_EDGE_LIVE", matches = "true")
    void synthesizesRealAudio() throws Exception {
        assertTrue(EdgeVoiceCatalog.refreshAsync().get(40, TimeUnit.SECONDS).size() > 100);
        TtsConfig config = new TtsConfig();
        config.provider = TtsConfig.Provider.EDGE_TTS;
        config.locale = "pt-BR";
        config.edgeVoice = "pt-BR-FranciscaNeural";
        short[] samples = new EdgeTtsProvider().synthesize("Teste de voz do Open Your Mouth.", config);
        assertTrue(samples.length > 48_000 / 2);
    }
}
