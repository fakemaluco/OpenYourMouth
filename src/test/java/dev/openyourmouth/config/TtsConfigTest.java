package dev.openyourmouth.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TtsConfigTest {
    @Test
    void sendsChatMessagesByDefault() {
        TtsConfig config = new TtsConfig();
        assertTrue(config.sendChatMessages);
        assertFalse(config.ignoreAccents);
        assertFalse(config.ignoreUppercase);
        assertEquals(15, TtsConfig.CURRENT_SCHEMA);
    }

    @Test
    void migratesSchema12AndDropsRemovedSpellingSetting() {
        Gson gson = new Gson();
        TtsConfig config = gson.fromJson("""
                {
                  "schemaVersion": 12,
                  "correctSpelling": true,
                  "ignoreAccents": true,
                  "ignoreUppercase": true
                }
                """, TtsConfig.class);

        config.normalize();

        assertEquals(TtsConfig.CURRENT_SCHEMA, config.schemaVersion);
        assertTrue(config.ignoreAccents);
        assertTrue(config.ignoreUppercase);
        assertFalse(gson.toJson(config).contains("correctSpelling"));
    }

    @Test
    void suppliesAndClampsFishDefaults() {
        TtsConfig config = new TtsConfig();
        assertEquals("s2-pro", config.fishModel);
        assertEquals(TtsConfig.DEFAULT_FISH_ENDPOINT, config.fishEndpoint);
        assertEquals(0.5D, config.volume);
        assertFalse(config.fishAdvancedSettings);
        config.fishSpeed = 9.0D;
        config.fishTemperature = -1.0D;
        config.fishTopP = 4.0D;
        config.fishLatency = "invalid";
        config.normalize();
        assertEquals(2.0D, config.fishSpeed);
        assertEquals(0.0D, config.fishTemperature);
        assertEquals(1.0D, config.fishTopP);
        assertEquals("balanced", config.fishLatency);
    }

    @Test
    void clampsValuesAndRequiresConsent() {
        TtsConfig config = new TtsConfig();
        config.enabled = true;
        config.consentAccepted = false;
        config.volume = 99;
        config.pitch = -99;
        config.speakingRate = 0;
        config.normalize();
        assertFalse(config.enabled);
        assertEquals(2.0D, config.volume);
        assertEquals(-20.0D, config.pitch);
        assertEquals(0.25D, config.speakingRate);
    }

    @Test
    void suppliesAndNormalizesSkipTrigger() {
        TtsConfig config = new TtsConfig();
        assertEquals("-skip", config.skipTrigger);
        config.skipTrigger = "  !skip  ";
        config.normalize();
        assertEquals("!skip", config.skipTrigger);
        config.skipTrigger = " ";
        config.normalize();
        assertEquals("-skip", config.skipTrigger);
    }

    @Test
    void preservesCustomSkipCommandChoice() {
        TtsConfig config = new TtsConfig();
        config.skipTrigger = "#parar";
        config.normalize();
        assertTrue(config.customSkipCommand);
        assertFalse(TtsConfig.isPresetSkipTrigger(config.skipTrigger));
    }

    @Test
    void normalizesAndDeduplicatesFishVoiceCatalog() {
        TtsConfig config = new TtsConfig();
        config.fishVoiceCatalog = new ArrayList<>(List.of(
                new FishVoiceModel("  Minha voz  ", " voice-1 "),
                new FishVoiceModel("Duplicada", "VOICE-1"),
                new FishVoiceModel("", "voice-2"),
                new FishVoiceModel("Sem ID", " ")
        ));

        config.normalize();

        assertEquals(2, config.fishVoiceCatalog.size());
        assertEquals("Minha voz", config.fishVoiceCatalog.getFirst().name);
        assertEquals("voice-1", config.fishVoiceCatalog.getFirst().id);
        assertEquals("voice-2", config.fishVoiceCatalog.get(1).name);
        assertEquals(TtsConfig.CURRENT_SCHEMA, config.schemaVersion);
        assertEquals(FishVoiceModel.Source.MANUAL, config.fishVoiceCatalog.getFirst().source);
    }

    @Test
    void preservesEveryKnownFishModelWithoutLegacyMigration() {
        for (String model : List.of("s2-pro", "s1", "s2.1-pro", "s2.1-pro-free")) {
            TtsConfig official = new TtsConfig();
            official.schemaVersion = 7;
            official.fishModel = model;
            official.normalize();
            assertEquals(model, official.fishModel);

            TtsConfig selfHosted = new TtsConfig();
            selfHosted.schemaVersion = 7;
            selfHosted.fishEndpoint = "http://127.0.0.1:8080/v1/tts";
            selfHosted.fishModel = model;
            selfHosted.normalize();
            assertEquals(model, selfHosted.fishModel);
        }
    }

    @Test
    void migratesOnlyTheOldDefaultVolume() {
        TtsConfig oldDefault = new TtsConfig();
        oldDefault.schemaVersion = 8;
        oldDefault.volume = 1.0D;
        oldDefault.normalize();
        assertEquals(0.5D, oldDefault.volume);

        TtsConfig oldCustom = new TtsConfig();
        oldCustom.schemaVersion = 8;
        oldCustom.volume = 0.8D;
        oldCustom.normalize();
        assertEquals(0.8D, oldCustom.volume);

        TtsConfig currentCustom = new TtsConfig();
        currentCustom.volume = 1.0D;
        currentCustom.normalize();
        assertEquals(1.0D, currentCustom.volume);
    }

    @Test
    void keepsFishAdvancedExpansionAndDetectsCustomModels() {
        TtsConfig config = new TtsConfig();
        config.fishAdvancedSettings = true;
        config.fishModel = "community-model";
        config.normalize();
        assertTrue(config.fishAdvancedSettings);
        assertTrue(config.fishCustomModel);
        assertEquals("community-model", config.fishModel);
    }

    @Test
    void manualCatalogEntryWinsOverSyncedDuplicate() {
        TtsConfig config = new TtsConfig();
        config.fishVoiceCatalog = new ArrayList<>(List.of(
                new FishVoiceModel("Cloud", "same", FishVoiceModel.Source.ACCOUNT),
                new FishVoiceModel("Meu nome", "SAME", FishVoiceModel.Source.MANUAL)
        ));
        config.normalize();
        assertEquals(1, config.fishVoiceCatalog.size());
        assertEquals("Meu nome", config.fishVoiceCatalog.getFirst().name);
        assertEquals(FishVoiceModel.Source.MANUAL, config.fishVoiceCatalog.getFirst().source);
    }

    @Test
    void fishQualityProfilesMapToLatencyValues() {
        assertEquals(FishQualityProfile.QUALITY, FishQualityProfile.fromLatency("normal"));
        assertEquals(FishQualityProfile.BALANCED, FishQualityProfile.fromLatency("balanced"));
        assertEquals(FishQualityProfile.FAST, FishQualityProfile.fromLatency("low"));
        assertEquals(FishQualityProfile.BALANCED, FishQualityProfile.fromLatency("unknown"));
    }

    @Test
    void fishBaseModelSelectorPreservesKnownAndCustomValues() {
        assertEquals(FishBaseModel.S2_PRO, FishBaseModel.fromId("s2-pro"));
        assertEquals(FishBaseModel.S1, FishBaseModel.fromId("S1"));
        assertEquals(FishBaseModel.S21_PRO, FishBaseModel.fromId("s2.1-pro"));
        assertEquals(FishBaseModel.S21_PRO_FREE, FishBaseModel.fromId("s2.1-pro-free"));
        assertEquals(FishBaseModel.OTHER, FishBaseModel.fromId("my-custom-model"));
        assertEquals(FishBaseModel.OTHER, FishBaseModel.fromId(null));
    }

    @Test
    void suppliesAndClampsVoicevoxDefaults() {
        TtsConfig config = new TtsConfig();
        assertEquals(TtsConfig.DEFAULT_VOICEVOX_ENDPOINT, config.voicevoxEndpoint);
        config.voicevoxStyleId = -9;
        config.voicevoxSpeed = 9.0D;
        config.voicevoxPitch = -2.0D;
        config.voicevoxIntonation = 8.0D;
        config.normalize();
        assertEquals(0, config.voicevoxStyleId);
        assertEquals(2.0D, config.voicevoxSpeed);
        assertEquals(-0.15D, config.voicevoxPitch);
        assertEquals(2.0D, config.voicevoxIntonation);
    }

    @Test
    void migratesRemovedLocalProvidersAndDropsTheirSettings() {
        Gson gson = new Gson();
        for (String provider : List.of("QWEN3_TTS", "AUDIO8_TTS")) {
            TtsConfig config = gson.fromJson("""
                    {
                      "schemaVersion": 14,
                      "provider": "%s",
                      "qwenLanguage": "Portuguese",
                      "qwenReferenceFile": "qwen.wav",
                      "qwenReferenceText": "texto privado",
                      "audio8ReferenceFile": "audio8.wav",
                      "audio8ReferenceText": "texto privado",
                      "audio8Temperature": 0.8
                    }
                    """.formatted(provider), TtsConfig.class);

            config.normalize();
            String serialized = gson.toJson(config);

            assertEquals(TtsConfig.CURRENT_SCHEMA, config.schemaVersion);
            assertEquals(TtsConfig.Provider.GOOGLE_TRANSLATE, config.provider);
            assertFalse(serialized.toLowerCase().contains("qwen"));
            assertFalse(serialized.toLowerCase().contains("audio8"));
        }
    }

    @Test
    void appendsNewProvidersWithoutChangingLegacyOrder() {
        assertArrayEquals(new TtsConfig.Provider[]{
                TtsConfig.Provider.GOOGLE_TRANSLATE,
                TtsConfig.Provider.EDGE_TTS,
                TtsConfig.Provider.FISH_AUDIO,
                TtsConfig.Provider.VOICEVOX
        }, TtsConfig.Provider.values());
    }
}
