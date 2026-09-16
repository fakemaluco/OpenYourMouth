package dev.openyourmouth.tts;

import java.util.List;

/** Locales supported by the built-in online TTS providers. */
public final class SpokenLanguageOptions {
    public static final List<String> LOCALES = List.of(
            "pt-BR", "pt-PT", "en-US", "en-GB", "en-AU", "es-ES", "es-MX", "fr-FR", "fr-CA",
            "de-DE", "it-IT", "nl-NL", "pl-PL", "cs-CZ", "da-DK", "fi-FI", "nb-NO", "sv-SE",
            "ro-RO", "tr-TR", "uk-UA", "ru-RU", "el-GR", "ar-XA", "hi-IN", "bn-IN", "id-ID",
            "vi-VN", "th-TH", "ja-JP", "ko-KR", "zh-CN", "zh-TW"
    );

    private SpokenLanguageOptions() {}
}
