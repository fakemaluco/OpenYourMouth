package dev.openyourmouth.tts;

/** A speakable VOICEVOX character style returned by the engine's /speakers endpoint. */
public record VoicevoxStyle(String name, int id) {
    public VoicevoxStyle {
        name = name == null || name.isBlank() ? Integer.toString(id) : name.strip();
        if (id < 0) throw new IllegalArgumentException("VOICEVOX style ID cannot be negative");
    }
}
