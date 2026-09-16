package dev.openyourmouth.tts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SpeechTextNormalizerTest {
    @Test
    void removesLatinDiacriticsWhenEnabled() {
        assertEquals("O pinguim falou: coracao, acao, avo e voce.",
                SpeechTextNormalizer.forSynthesis("O pingüim falou: coração, ação, avó e você.", true));
        assertEquals("CAFE SAO JOAO", SpeechTextNormalizer.forSynthesis("CAFÉ SÃO JOÃO", true));
    }

    @Test
    void leavesTheOriginalTextUntouchedWhenDisabled() {
        String text = "Olá, você está bem?";
        assertSame(text, SpeechTextNormalizer.forSynthesis(text, false));
    }

    @Test
    void preservesNonLatinMarksAndEmojiVariationSelectors() {
        assertEquals("がぎぐ ©️", SpeechTextNormalizer.forSynthesis("がぎぐ ©️", true));
    }

    @Test
    void lowercasesOnlyTheSynthesizedTextWhenRequested() {
        assertEquals("olá brasil 123", SpeechTextNormalizer.forSynthesis(
                "OLÁ Brasil 123", false, true));
    }

    @Test
    void combinesLowercaseAndAccentRemoval() {
        assertEquals("voce nao esta aqui", SpeechTextNormalizer.forSynthesis(
                "VOCÊ NÃO ESTÁ AQUI", true, true));
    }

    @Test
    void doesNotCorrectWritingOrPunctuation() {
        assertEquals(" oi ,vc nao vai pq concerteza ", SpeechTextNormalizer.forSynthesis(
                " oi ,VC nao vai pq concerteza ", false, true));
    }
}
