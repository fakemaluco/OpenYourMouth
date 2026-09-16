package dev.openyourmouth.config;

public enum FishQualityProfile {
    QUALITY("normal"), BALANCED("balanced"), FAST("low");

    private final String latency;

    FishQualityProfile(String latency) {
        this.latency = latency;
    }

    public String latency() { return latency; }

    public static FishQualityProfile fromLatency(String value) {
        for (FishQualityProfile profile : values()) {
            if (profile.latency.equals(value)) return profile;
        }
        return BALANCED;
    }
}
