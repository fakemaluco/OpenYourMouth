package dev.openyourmouth.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TtsConfig {
    public static final int CURRENT_SCHEMA = 15;
    public static final String DEFAULT_FISH_ENDPOINT = "https://api.fish.audio/v1/tts";
    public static final String DEFAULT_VOICEVOX_ENDPOINT = "http://127.0.0.1:50021";
    public static final String DEFAULT_SKIP_TRIGGER = "-skip";

    public int schemaVersion = CURRENT_SCHEMA;
    public boolean consentAccepted;
    public boolean enabled;
    public boolean hearSelf;
    public boolean sendChatMessages = true;
    /** Removes Latin diacritics only from text sent to TTS; chat text is never changed. */
    public boolean ignoreAccents;
    /** Lowercases only text sent to TTS. */
    public boolean ignoreUppercase;
    public Backend backend = Backend.AUTO;
    public Provider provider = Provider.GOOGLE_TRANSLATE;
    public String locale = "pt-BR";
    public String edgeVoice = "";
    public String fishEndpoint = DEFAULT_FISH_ENDPOINT;
    public String fishApiKey = "";
    public String fishModel = "s2-pro";
    public boolean fishCustomModel;
    public String fishReferenceId = "";
    public List<FishVoiceModel> fishVoiceCatalog = new ArrayList<>();
    public String fishLatency = "balanced";
    public double fishSpeed = 1.0D;
    public double fishTemperature = 0.7D;
    public double fishTopP = 0.7D;
    public boolean fishNormalize = true;
    public String voicevoxEndpoint = DEFAULT_VOICEVOX_ENDPOINT;
    public int voicevoxStyleId;
    public String voicevoxStyleName = "";
    public double voicevoxSpeed = 1.0D;
    public double voicevoxPitch;
    public double voicevoxIntonation = 1.0D;
    public double speakingRate = 1.0D;
    public double pitch = 0.0D;
    public double volume = 0.5D;
    public String skipTrigger = DEFAULT_SKIP_TRIGGER;
    /** True when the user selected the "Other" skip-command option. */
    public boolean customSkipCommand;
    /** UI preference. The simple view remains the default for new installations. */
    public boolean advancedSettings;
    /** Persistent expansion state for provider-specific Fish settings. */
    public boolean fishAdvancedSettings;
    public enum Backend { AUTO, SIMPLE_VOICE_CHAT, PLASMO_VOICE }
    public enum Provider { GOOGLE_TRANSLATE, EDGE_TTS, FISH_AUDIO, VOICEVOX }

    public void normalize() {
        int previousSchema = schemaVersion;
        schemaVersion = CURRENT_SCHEMA;
        if (backend == null) backend = Backend.AUTO;
        if (provider == null) provider = Provider.GOOGLE_TRANSLATE;
        if (locale == null || locale.isBlank()) locale = "pt-BR";
        if (edgeVoice == null) edgeVoice = "";
        if (fishEndpoint == null || fishEndpoint.isBlank()) fishEndpoint = DEFAULT_FISH_ENDPOINT;
        if (fishApiKey == null) fishApiKey = "";
        if (fishModel == null || fishModel.isBlank()) fishModel = "s2-pro";
        if (FishBaseModel.fromId(fishModel) == FishBaseModel.OTHER) fishCustomModel = true;
        if (previousSchema < 9 && Double.compare(volume, 1.0D) == 0) volume = 0.5D;
        if (fishReferenceId == null) fishReferenceId = "";
        normalizeFishVoiceCatalog();
        if (fishLatency == null || !(fishLatency.equals("low") || fishLatency.equals("normal") || fishLatency.equals("balanced"))) fishLatency = "balanced";
        if (skipTrigger == null || skipTrigger.isBlank()) skipTrigger = DEFAULT_SKIP_TRIGGER;
        skipTrigger = skipTrigger.strip();
        if (!isPresetSkipTrigger(skipTrigger)) customSkipCommand = true;
        speakingRate = Math.max(0.25D, Math.min(4.0D, speakingRate));
        pitch = Math.max(-20.0D, Math.min(20.0D, pitch));
        volume = Math.max(0.0D, Math.min(2.0D, volume));
        fishSpeed = Math.max(0.5D, Math.min(2.0D, fishSpeed));
        fishTemperature = Math.max(0.0D, Math.min(1.0D, fishTemperature));
        fishTopP = Math.max(0.0D, Math.min(1.0D, fishTopP));
        if (voicevoxEndpoint == null || voicevoxEndpoint.isBlank()) voicevoxEndpoint = DEFAULT_VOICEVOX_ENDPOINT;
        else voicevoxEndpoint = voicevoxEndpoint.strip();
        voicevoxStyleId = Math.max(0, voicevoxStyleId);
        if (voicevoxStyleName == null) voicevoxStyleName = "";
        else voicevoxStyleName = voicevoxStyleName.strip();
        voicevoxSpeed = Math.max(0.5D, Math.min(2.0D, voicevoxSpeed));
        voicevoxPitch = Math.max(-0.15D, Math.min(0.15D, voicevoxPitch));
        voicevoxIntonation = Math.max(0.0D, Math.min(2.0D, voicevoxIntonation));
        if (!consentAccepted) enabled = false;
    }

    public static boolean isPresetSkipTrigger(String value) {
        return DEFAULT_SKIP_TRIGGER.equals(value) || "!skip".equals(value) || ".skip".equals(value) || "tts-skip".equals(value);
    }

    private void normalizeFishVoiceCatalog() {
        if (fishVoiceCatalog == null) fishVoiceCatalog = new ArrayList<>();
        Map<String, FishVoiceModel> unique = new LinkedHashMap<>();
        for (FishVoiceModel voice : fishVoiceCatalog) {
            if (voice == null || voice.id == null || voice.id.isBlank()) continue;
            String id = voice.id.strip();
            String name = voice.name == null || voice.name.isBlank() ? id : voice.name.strip();
            FishVoiceModel.Source source = voice.source == null ? FishVoiceModel.Source.MANUAL : voice.source;
            String key = id.toLowerCase(Locale.ROOT);
            FishVoiceModel normalized = new FishVoiceModel(name, id, source);
            FishVoiceModel existing = unique.get(key);
            if (existing == null || (existing.source == FishVoiceModel.Source.ACCOUNT && source == FishVoiceModel.Source.MANUAL)) {
                unique.put(key, normalized);
            }
            if (unique.size() >= 500) break;
        }
        fishVoiceCatalog = new ArrayList<>(unique.values());
    }

    public static boolean isOfficialFishEndpoint(String endpoint) {
        if (endpoint == null) return false;
        try {
            return "api.fish.audio".equalsIgnoreCase(java.net.URI.create(endpoint.strip()).getHost());
        } catch (Exception ignored) {
            return false;
        }
    }

}
