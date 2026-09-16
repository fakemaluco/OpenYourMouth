package dev.openyourmouth.config;

/** A locally saved Fish Audio voice model. */
public final class FishVoiceModel {
    public enum Source { MANUAL, ACCOUNT }

    public String name = "";
    public String id = "";
    public Source source = Source.MANUAL;

    public FishVoiceModel() {
    }

    public FishVoiceModel(String name, String id) {
        this(name, id, Source.MANUAL);
    }

    public FishVoiceModel(String name, String id, Source source) {
        this.name = name;
        this.id = id;
        this.source = source;
    }
}
