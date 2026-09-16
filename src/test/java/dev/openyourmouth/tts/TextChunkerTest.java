package dev.openyourmouth.tts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextChunkerTest {
    @Test
    void keepsUnicodeCodePointsIntact() {
        String text = "😀".repeat(181);
        var chunks = TextChunker.split(text, 180);
        assertEquals(2, chunks.size());
        assertEquals(180, chunks.getFirst().codePointCount(0, chunks.getFirst().length()));
        assertEquals(1, chunks.getLast().codePointCount(0, chunks.getLast().length()));
    }

    @Test
    void prefersSentenceBoundary() {
        String first = "a".repeat(130) + ". ";
        var chunks = TextChunker.split(first + "b".repeat(100), 180);
        assertEquals(first.strip(), chunks.getFirst());
        assertEquals(100, chunks.getLast().length());
    }

    @Test
    void ignoresOuterWhitespace() {
        assertEquals("hello", TextChunker.split("  hello  ", 180).getFirst());
    }

    @Test
    void splitsLongCloudInputWithoutLosingText() {
        String input = "palavra ".repeat(900).strip();
        var chunks = TextChunker.split(input, 1_000);
        assertTrue(chunks.size() > 1);
        assertEquals(input.replace(" ", ""), String.join("", chunks).replace(" ", ""));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.codePointCount(0, chunk.length()) <= 1_000));
    }
}
