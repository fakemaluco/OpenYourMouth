package dev.openyourmouth.integration.plasmo;

import dev.openyourmouth.OpenYourMouthClient;
import dev.openyourmouth.TtsController;
import dev.openyourmouth.config.FishBaseModel;
import dev.openyourmouth.config.FishVoiceModel;
import dev.openyourmouth.config.TtsConfig;
import net.minecraft.client.Minecraft;
import su.plo.config.entry.DoubleConfigEntry;
import su.plo.lib.mod.client.gui.components.Button;
import su.plo.slib.api.chat.component.McTextComponent;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.client.config.VoiceClientConfig;
import su.plo.voice.client.gui.settings.VoiceSettingsScreen;
import su.plo.voice.client.gui.settings.tab.TabWidget;
import su.plo.voice.client.gui.settings.widget.DropDownWidget;
import su.plo.voice.client.gui.settings.widget.VolumeSliderWidget;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Fish setup and advanced controls in one native, scrollable Plasmo tab. */
public final class PlasmoFishTabWidget extends TabWidget {
    private static final List<String> LATENCIES = List.of("normal", "balanced", "low");
    private final VoiceSettingsScreen screen;
    private CommitTextFieldWidget apiKey;
    private CommitTextFieldWidget endpoint;
    private CommitTextFieldWidget referenceId;
    private CommitTextFieldWidget customModel;
    private boolean revealKey;
    private boolean active;

    public PlasmoFishTabWidget(VoiceSettingsScreen screen, PlasmoVoiceClient voiceClient, VoiceClientConfig config) {
        super(screen, voiceClient, config);
        this.screen = screen;
    }

    @Override
    public void init() {
        super.init();
        active = true;
        endpoint = null;
        referenceId = null;
        customModel = null;
        TtsConfig core = core();

        apiKey = field("gui.openyourmouth.fish.api_key", core.fishApiKey, 512, value -> commitApiKey(value.strip()));
        updateKeyFormatter();
        addEntry(new OptionEntry<>(text("gui.openyourmouth.fish.api_key"), apiKey, null,
                text("gui.openyourmouth.fish.api_key.tooltip")));
        addCatalogMessageIfNeeded();

        addVoiceSelector(core);
        addModelSelector(core);
        if (core.fishCustomModel) addCustomModel(core);
        addLatencySelector(core);

        Button advanced = new Button(0, 0, 124, 20, toggleName(core.fishAdvancedSettings), ignored -> {
            commitTextFields();
            core.fishAdvancedSettings = !core.fishAdvancedSettings;
            save();
            init();
        }, Button.NO_TOOLTIP);
        addEntry(new OptionEntry<>(text("gui.openyourmouth.fish.advanced.toggle"), advanced, null));

        if (core.fishAdvancedSettings) addAdvanced(core);

        TtsController.FishCatalogSnapshot catalog = controller().fishCatalogStatus();
        if (!core.fishApiKey.isBlank() && catalog.state() == TtsController.CatalogState.IDLE) sync(false);
    }

    private void addCatalogMessageIfNeeded() {
        TtsController.FishCatalogSnapshot status = controller().fishCatalogStatus();
        if (status.state() != TtsController.CatalogState.SYNCING && status.state() != TtsController.CatalogState.ERROR) return;
        Button message = new Button(0, 0, 124, 20, statusText(status), Button.NO_ACTION, Button.NO_TOOLTIP);
        message.setActive(false);
        addEntry(new OptionEntry<>(text("gui.openyourmouth.status.label"), message, null));
    }

    private void addVoiceSelector(TtsConfig core) {
        List<FishVoiceModel> voices = core.fishVoiceCatalog.stream()
                .sorted(Comparator.comparing((FishVoiceModel voice) -> voice.source == FishVoiceModel.Source.ACCOUNT ? 0 : 1)
                        .thenComparing(voice -> voice.name, String.CASE_INSENSITIVE_ORDER)).toList();
        FishVoiceModel selected = voices.stream().filter(voice -> voice.id.equalsIgnoreCase(core.fishReferenceId)).findFirst().orElse(null);
        if (voices.isEmpty()) {
            Button empty = new Button(0, 0, 124, 20, text("gui.openyourmouth.voice.not_selected"), Button.NO_ACTION, Button.NO_TOOLTIP);
            empty.setActive(false);
            addEntry(new OptionEntry<>(text("gui.openyourmouth.voice.label"), empty, null,
                    text("gui.openyourmouth.fish.catalog.tooltip")));
            return;
        }
        DropDownWidget voice = new DropDownWidget(screen, 0, 0, 124, 20,
                selected == null ? text("gui.openyourmouth.voice.not_selected") : McTextComponent.literal(shorten(selected.name, 28)),
                voices.stream().map(item -> (McTextComponent) McTextComponent.literal(shorten(item.name, 36))).toList(), false, index -> {
            core.fishReferenceId = voices.get(index).id;
            save();
        });
        addEntry(new OptionEntry<>(text("gui.openyourmouth.voice.label"), voice, null,
                text("gui.openyourmouth.fish.catalog.tooltip")));
    }

    private void addModelSelector(TtsConfig core) {
        List<FishBaseModel> models = List.of(FishBaseModel.values());
        FishBaseModel selected = core.fishCustomModel ? FishBaseModel.OTHER : FishBaseModel.fromId(core.fishModel);
        DropDownWidget model = new DropDownWidget(screen, 0, 0, 124, 20, modelName(selected),
                models.stream().map(PlasmoFishTabWidget::modelName).toList(), false, index -> {
            commitTextFields();
            FishBaseModel value = models.get(index);
            core.fishCustomModel = value == FishBaseModel.OTHER;
            if (!core.fishCustomModel) core.fishModel = value.id();
            save();
            init();
        });
        addEntry(new OptionEntry<>(text("gui.openyourmouth.fish.model.label"), model, null,
                text("gui.openyourmouth.fish.model.tooltip")));
    }

    private void addCustomModel(TtsConfig core) {
        customModel = field("gui.openyourmouth.fish.model.custom", core.fishModel, 64, value -> {
            if (value.isBlank()) return;
            core.fishModel = value.strip();
            core.fishCustomModel = true;
            save();
        });
        addEntry(new OptionEntry<>(text("gui.openyourmouth.fish.model.custom"), customModel, null));
    }

    private void addLatencySelector(TtsConfig core) {
        int current = Math.max(0, LATENCIES.indexOf(core.fishLatency));
        DropDownWidget latency = new DropDownWidget(screen, 0, 0, 124, 20, latencyName(LATENCIES.get(current)),
                LATENCIES.stream().map(PlasmoFishTabWidget::latencyName).toList(), false, index -> {
            core.fishLatency = LATENCIES.get(index);
            save();
        });
        addEntry(new OptionEntry<>(text("gui.openyourmouth.fish.latency.label"), latency, null));
    }

    private void addAdvanced(TtsConfig core) {
        endpoint = field("gui.openyourmouth.fish.endpoint", core.fishEndpoint, 512, value -> {
            core.fishEndpoint = value.isBlank() ? TtsConfig.DEFAULT_FISH_ENDPOINT : value.strip();
            save();
        });
        addEntry(new OptionEntry<>(text("gui.openyourmouth.fish.endpoint"), endpoint, null,
                text("gui.openyourmouth.fish.endpoint.tooltip")));

        referenceId = field("gui.openyourmouth.fish.reference_id", core.fishReferenceId, 128, value -> {
            core.fishReferenceId = value.strip();
            save();
        });
        addEntry(new OptionEntry<>(text("gui.openyourmouth.fish.reference_id"), referenceId, null,
                text("gui.openyourmouth.fish.reference_id.tooltip")));

        DoubleConfigEntry speedEntry = new DoubleConfigEntry(core.fishSpeed, 0.5D, 2.0D);
        speedEntry.addChangeListener(value -> { core.fishSpeed = value; save(); });
        addEntry(new OptionEntry<>(text("gui.openyourmouth.fish.speed.label"),
                new VolumeSliderWidget(voiceClient.getHotkeys(), speedEntry, "%", 0, 0, 124, 20), speedEntry));

        DoubleConfigEntry temperatureEntry = new DoubleConfigEntry(core.fishTemperature, 0.0D, 1.0D);
        temperatureEntry.addChangeListener(value -> { core.fishTemperature = value; save(); });
        addEntry(new OptionEntry<>(text("gui.openyourmouth.fish.temperature.label"),
                new VolumeSliderWidget(voiceClient.getHotkeys(), temperatureEntry, "%", 0, 0, 124, 20), temperatureEntry));

        DoubleConfigEntry topPEntry = new DoubleConfigEntry(core.fishTopP, 0.0D, 1.0D);
        topPEntry.addChangeListener(value -> { core.fishTopP = value; save(); });
        addEntry(new OptionEntry<>(text("gui.openyourmouth.fish.top_p.label"),
                new VolumeSliderWidget(voiceClient.getHotkeys(), topPEntry, "%", 0, 0, 124, 20), topPEntry));

        Button normalize = new Button(0, 0, 124, 20, toggleName(core.fishNormalize), button -> {
            core.fishNormalize = !core.fishNormalize;
            button.setText(toggleName(core.fishNormalize));
            save();
        }, Button.NO_TOOLTIP);
        addEntry(new OptionEntry<>(text("gui.openyourmouth.fish.normalize.label"), normalize, null));

        Button reveal = new Button(0, 0, 124, 20, text(revealKey
                ? "gui.openyourmouth.fish.hide_key" : "gui.openyourmouth.fish.show_key"), button -> {
            revealKey = !revealKey;
            button.setText(text(revealKey ? "gui.openyourmouth.fish.hide_key" : "gui.openyourmouth.fish.show_key"));
            updateKeyFormatter();
            apiKey.setValue(apiKey.getValue());
        }, Button.NO_TOOLTIP);
        addEntry(new OptionEntry<>(text("gui.openyourmouth.fish.show_key"), reveal, null));

        Button refresh = new Button(0, 0, 124, 20, text("gui.openyourmouth.fish.refresh"), ignored -> {
            commitTextFields();
            sync(true);
        }, Button.NO_TOOLTIP);
        addEntry(new OptionEntry<>(text("gui.openyourmouth.fish.refresh"), refresh, null,
                text("gui.openyourmouth.fish.catalog.tooltip")));
    }

    private CommitTextFieldWidget field(String key, String value, int maximum, java.util.function.Consumer<String> commit) {
        return new CommitTextFieldWidget(124, text(key), value, maximum, commit);
    }

    private void commitApiKey(String value) {
        TtsConfig core = core();
        if (core.fishApiKey.equals(value)) return;
        core.fishApiKey = value;
        save();
        controller().resetFishCatalogSession();
        if (!value.isBlank()) sync(true);
    }

    private void commitTextFields() {
        if (apiKey != null) apiKey.commitNow();
        if (endpoint != null) endpoint.commitNow();
        if (referenceId != null) referenceId.commitNow();
        if (customModel != null) customModel.commitNow();
    }

    private void sync(boolean force) {
        controller().syncFishCatalog(force).whenComplete((ignored, error) -> Minecraft.getInstance().execute(() -> {
            if (active) init();
        }));
    }

    private void updateKeyFormatter() {
        if (apiKey != null) apiKey.setFormatter((value, offset) ->
                McTextComponent.literal(revealKey ? value : "•".repeat(value.length())));
    }

    @Override
    public void removed() {
        active = false;
        commitTextFields();
        super.removed();
    }

    private static McTextComponent statusText(TtsController.FishCatalogSnapshot status) {
        String key = status.state() == TtsController.CatalogState.SYNCING
                ? "gui.openyourmouth.fish.status.connecting"
                : "gui.openyourmouth.fish.status.error." + status.error().toLowerCase(Locale.ROOT);
        return text(key);
    }

    private static McTextComponent modelName(FishBaseModel model) { return text(model.translationKey()); }
    private static McTextComponent latencyName(String value) { return text("gui.openyourmouth.fish.latency." + value); }
    private static McTextComponent toggleName(boolean value) { return text(value ? "options.on" : "options.off"); }
    private static String shorten(String value, int maximum) { return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…"; }
    private static TtsController controller() { return OpenYourMouthClient.controller(); }
    private static TtsConfig core() { return controller().config(); }
    private static void save() { controller().saveConfig(); }
    private static McTextComponent text(String key) { return McTextComponent.translatable(key); }
}
