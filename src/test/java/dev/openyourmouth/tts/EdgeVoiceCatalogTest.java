package dev.openyourmouth.tts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EdgeVoiceCatalogTest {
    @Test
    void gecIsStableInsideFiveMinuteWindow() throws Exception {
        String first = EdgeVoiceCatalog.generateGec(1_700_000_101L);
        String second = EdgeVoiceCatalog.generateGec(1_700_000_399L);
        assertEquals(first, second);
        assertTrue(first.matches("[0-9A-F]{64}"));
        assertNotEquals(first, EdgeVoiceCatalog.generateGec(1_700_000_400L));
    }

    @Test
    void voicesAreSelectedForTheRequestedLanguage() {
        assertTrue(EdgeVoiceCatalog.forLocale("pt-BR").stream().allMatch(value -> value.startsWith("pt-BR-")));
        assertTrue(EdgeVoiceCatalog.selectedOrDefault("ja-JP", "").startsWith("ja-JP-"));
        assertNotEquals(EdgeVoiceCatalog.next("pt-BR", "pt-BR-AntonioNeural"), "");
    }
}
