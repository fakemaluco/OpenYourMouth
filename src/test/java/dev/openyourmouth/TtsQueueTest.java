package dev.openyourmouth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TtsQueueTest {
    @Test
    void acceptsFiveItemsAndRejectsTheSixth() {
        TtsQueue queue = new TtsQueue();
        for (int index = 0; index < TtsQueue.CAPACITY; index++) {
            assertTrue(queue.offer("message-" + index));
        }
        assertFalse(queue.offer("message-5"));
        assertEquals(5, queue.size());
    }

    @Test
    void preservesFifoOrderAndClears() throws Exception {
        TtsQueue queue = new TtsQueue();
        queue.offer("first");
        queue.offer("second");
        assertEquals("first", queue.take());
        assertEquals("second", queue.take());
        queue.offer("third");
        queue.clear();
        assertEquals(0, queue.size());
    }
}
