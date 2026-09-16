package dev.openyourmouth.tts;

public final class TtsHttpException extends Exception {
    private final int status;

    public TtsHttpException(int status, String message) {
        super(message + " (HTTP " + status + ")");
        this.status = status;
    }

    public int status() { return status; }
}
