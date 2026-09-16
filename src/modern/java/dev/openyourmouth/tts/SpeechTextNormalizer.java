package dev.openyourmouth.tts;

import java.text.Normalizer;
import java.util.Locale;

/** Optional text preparation shared by every provider. */
public final class SpeechTextNormalizer {
    private SpeechTextNormalizer() {}

    public static String forSynthesis(String text, boolean ignoreAccents) {
        return forSynthesis(text, ignoreAccents, false);
    }

    public static String forSynthesis(String text, boolean ignoreAccents, boolean ignoreUppercase) {
        if (text == null || text.isEmpty()) return text;
        String value = text;
        if (ignoreUppercase) value = value.toLowerCase(Locale.ROOT);
        if (!ignoreAccents) return value;
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        StringBuilder result = new StringBuilder(decomposed.length());
        boolean latinBase = false;
        for (int offset = 0; offset < decomposed.length();) {
            int codePoint = decomposed.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            boolean combining = type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK;
            if (combining && latinBase && !isVariationSelector(codePoint)) continue;
            result.appendCodePoint(codePoint);
            if (!combining) latinBase = Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN;
        }
        return Normalizer.normalize(result, Normalizer.Form.NFC);
    }

    private static boolean isVariationSelector(int codePoint) {
        return codePoint >= 0xFE00 && codePoint <= 0xFE0F
                || codePoint >= 0xE0100 && codePoint <= 0xE01EF;
    }
}
