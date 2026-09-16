package dev.openyourmouth.tts;

import java.util.ArrayList;
import java.util.List;

public final class TextChunker {
    private TextChunker() {}

    public static List<String> split(String text, int maxCodePoints) {
        String remaining = text.strip();
        List<String> chunks = new ArrayList<>();
        while (!remaining.isEmpty()) {
            int count = remaining.codePointCount(0, remaining.length());
            if (count <= maxCodePoints) {
                chunks.add(remaining);
                break;
            }
            int hardEnd = remaining.offsetByCodePoints(0, maxCodePoints);
            int end = findBoundary(remaining, hardEnd);
            if (end <= 0) end = hardEnd;
            chunks.add(remaining.substring(0, end).strip());
            remaining = remaining.substring(end).strip();
        }
        return chunks;
    }

    private static int findBoundary(String text, int limit) {
        for (int i = limit - 1; i > Math.max(0, limit - 60); i--) {
            char value = text.charAt(i);
            if (value == '.' || value == '!' || value == '?' || value == ';' || Character.isWhitespace(value)) return i + 1;
        }
        return -1;
    }
}
