package dev.openyourmouth.config;

/** Known Fish TTS base models plus a custom value for compatible endpoints. */
public enum FishBaseModel {
    S2_PRO("s2-pro", "gui.openyourmouth.fish.model.s2"),
    S1("s1", "gui.openyourmouth.fish.model.s1"),
    S21_PRO("s2.1-pro", "gui.openyourmouth.fish.model.s21"),
    S21_PRO_FREE("s2.1-pro-free", "gui.openyourmouth.fish.model.free"),
    OTHER("", "gui.openyourmouth.fish.model.other");

    private final String id;
    private final String translationKey;

    FishBaseModel(String id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    public String id() { return id; }
    public String translationKey() { return translationKey; }

    public static FishBaseModel fromId(String value) {
        if (value != null) {
            for (FishBaseModel model : values()) {
                if (!model.id.isEmpty() && model.id.equalsIgnoreCase(value.strip())) return model;
            }
        }
        return OTHER;
    }
}
