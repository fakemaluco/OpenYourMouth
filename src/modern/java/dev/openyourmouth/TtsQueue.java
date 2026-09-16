package dev.openyourmouth;

import java.util.concurrent.ArrayBlockingQueue;

final class TtsQueue {
    static final int CAPACITY = 5;
    private final ArrayBlockingQueue<String> values = new ArrayBlockingQueue<>(CAPACITY);

    boolean offer(String text) { return values.offer(text); }
    String take() throws InterruptedException { return values.take(); }
    void clear() { values.clear(); }
    int size() { return values.size(); }
}
