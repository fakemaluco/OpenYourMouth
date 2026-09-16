package dev.openyourmouth.gui;

import dev.openyourmouth.OpenYourMouthClient;
import dev.openyourmouth.TtsController;
import dev.openyourmouth.config.FishBaseModel;
import dev.openyourmouth.config.FishVoiceModel;
import dev.openyourmouth.config.TtsConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Locale;

/** Unified Fish screen: four basic controls plus a persistent in-place advanced section. */
public final class FishSettingsScreen extends Screen {
    private static final List<String> LATENCIES = List.of("normal", "balanced", "low");
    private final Screen parent;
    private CommitEditBox apiKey;
    private CommitEditBox endpoint;
    private CommitEditBox referenceId;
    private CommitEditBox customModel;
    private Button refreshButton;
    private boolean revealApiKey;

    public FishSettingsScreen(Screen parent) {
        super(Component.translatable("gui.openyourmouth.fish.title.simple"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        TtsConfig config = config();
        int x = width / 2 - 150;
        int left = x;
        int right = x + 152;
        int y = 34;

        apiKey = field(left, y, 148, "gui.openyourmouth.fish.api_key", config.fishApiKey, 512,
                value -> commitApiKey(value.strip()));
        apiKey.setHint(Component.translatable("gui.openyourmouth.fish.api_key.hint"));
        updateKeyFormatter();
        addRenderableWidget(apiKey);
        y += 24;

        addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.voice", selectedVoice(config)), ignored ->
                minecraft.setScreen(new FishVoiceCatalogScreen(this))).bounds(left, y, 148, 20).build());
        y += 24;

        FishBaseModel selectedModel = config.fishCustomModel ? FishBaseModel.OTHER : FishBaseModel.fromId(config.fishModel);
        addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.fish.model", modelName(selectedModel)), ignored ->
                openModelSelector()).bounds(left, y, 148, 20).build());
        y += 24;

        if (config.fishCustomModel) {
            customModel = field(left, y, 148, "gui.openyourmouth.fish.model.custom", config.fishModel, 64, value -> {
                if (value.isBlank()) return;
                config.fishModel = value.strip();
                config.fishCustomModel = true;
                save();
            });
            customModel.setHint(Component.translatable("gui.openyourmouth.fish.model.custom"));
            addRenderableWidget(customModel);
            y += 24;
        } else {
            customModel = null;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.fish.latency", latencyName(config.fishLatency)), ignored ->
                openLatencySelector()).bounds(left, y, 148, 20).build());
        y += 24;

        addRenderableWidget(CycleButton.onOffBuilder(config.fishAdvancedSettings).create(left, y, 148, 20,
                Component.translatable("gui.openyourmouth.fish.advanced.toggle"), (button, value) -> {
                    commitTextFields();
                    config.fishAdvancedSettings = value;
                    save();
                    rebuildWidgets();
                }));

        endpoint = null;
        referenceId = null;
        refreshButton = null;
        if (config.fishAdvancedSettings) addAdvanced(config, right, 34);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(x, height - 24, 300, 20).build());

        TtsController.FishCatalogSnapshot catalog = controller().fishCatalogStatus();
        if (!config.fishApiKey.isBlank() && catalog.state() == TtsController.CatalogState.IDLE) beginSync(false);
    }

    private void addAdvanced(TtsConfig config, int x, int y) {
        endpoint = field(x, y, 148, "gui.openyourmouth.fish.endpoint", config.fishEndpoint, 512, value -> {
            config.fishEndpoint = value.isBlank() ? TtsConfig.DEFAULT_FISH_ENDPOINT : value.strip();
            save();
        });
        endpoint.setHint(Component.translatable("gui.openyourmouth.fish.endpoint"));
        addRenderableWidget(endpoint);
        y += 24;

        referenceId = field(x, y, 148, "gui.openyourmouth.fish.reference_id", config.fishReferenceId, 128, value -> {
            config.fishReferenceId = value.strip();
            save();
        });
        referenceId.setHint(Component.translatable("gui.openyourmouth.fish.reference_id"));
        addRenderableWidget(referenceId);
        y += 24;

        addRenderableWidget(new ValueSlider(x, y, 148, config.fishSpeed, 0.5D, 2.0D, 0.05D,
                value -> Component.translatable("gui.openyourmouth.fish.speed.percent", percent(value)),
                value -> { config.fishSpeed = value; save(); }));
        y += 24;

        addRenderableWidget(new ValueSlider(x, y, 148, config.fishTemperature, 0.0D, 1.0D, 0.05D,
                value -> Component.translatable("gui.openyourmouth.fish.temperature", percent(value)),
                value -> { config.fishTemperature = value; save(); }));
        y += 24;

        addRenderableWidget(new ValueSlider(x, y, 148, config.fishTopP, 0.0D, 1.0D, 0.05D,
                value -> Component.translatable("gui.openyourmouth.fish.top_p", percent(value)),
                value -> { config.fishTopP = value; save(); }));
        y += 24;

        addRenderableWidget(CycleButton.onOffBuilder(config.fishNormalize).create(x, y, 148, 20,
                Component.translatable("gui.openyourmouth.fish.normalize.label"), (button, value) -> {
                    config.fishNormalize = value;
                    save();
                }));
        y += 24;

        addRenderableWidget(Button.builder(Component.translatable(revealApiKey
                ? "gui.openyourmouth.fish.hide_key" : "gui.openyourmouth.fish.show_key"), button -> {
            revealApiKey = !revealApiKey;
            button.setMessage(Component.translatable(revealApiKey
                    ? "gui.openyourmouth.fish.hide_key" : "gui.openyourmouth.fish.show_key"));
            updateKeyFormatter();
            apiKey.setValue(apiKey.getValue());
        }).bounds(x, y, 72, 20).build());
        refreshButton = addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.fish.refresh"), ignored -> {
            commitTextFields();
            beginSync(true);
        }).bounds(x + 76, y, 72, 20).build());
    }

    private CommitEditBox field(int x, int y, int width, String key, String value, int maximum,
                                java.util.function.Consumer<String> commit) {
        return new CommitEditBox(font, x, y, width, Component.translatable(key), value, maximum, commit);
    }

    private void openModelSelector() {
        commitTextFields();
        TtsConfig config = config();
        List<FishOptionSelectionScreen.Choice> choices = List.of(FishBaseModel.values()).stream()
                .map(model -> new FishOptionSelectionScreen.Choice(model.name(), Component.translatable(model.translationKey()))).toList();
        String current = (config.fishCustomModel ? FishBaseModel.OTHER : FishBaseModel.fromId(config.fishModel)).name();
        minecraft.setScreen(new FishOptionSelectionScreen(this,
                Component.translatable("gui.openyourmouth.fish.model.select"), choices, current, value -> {
            FishBaseModel model = FishBaseModel.valueOf(value);
            config.fishCustomModel = model == FishBaseModel.OTHER;
            if (!config.fishCustomModel) config.fishModel = model.id();
            save();
        }));
    }

    private void openLatencySelector() {
        commitTextFields();
        TtsConfig config = config();
        List<FishOptionSelectionScreen.Choice> choices = LATENCIES.stream()
                .map(value -> new FishOptionSelectionScreen.Choice(value,
                        Component.translatable("gui.openyourmouth.fish.latency." + value))).toList();
        minecraft.setScreen(new FishOptionSelectionScreen(this,
                Component.translatable("gui.openyourmouth.fish.latency.select"), choices, config.fishLatency, value -> {
            config.fishLatency = value;
            save();
        }));
    }

    private void commitApiKey(String value) {
        TtsConfig config = config();
        if (config.fishApiKey.equals(value)) return;
        config.fishApiKey = value;
        save();
        controller().resetFishCatalogSession();
        if (!value.isBlank()) beginSync(true);
    }

    private void beginSync(boolean force) {
        controller().syncFishCatalog(force).whenComplete((ignored, error) -> minecraft.execute(() -> {
            if (minecraft.screen == this) rebuildWidgets();
        }));
    }

    private void commitTextFields() {
        if (apiKey != null) apiKey.commitNow();
        if (endpoint != null) endpoint.commitNow();
        if (referenceId != null) referenceId.commitNow();
        if (customModel != null) customModel.commitNow();
    }

    private void updateKeyFormatter() {
        if (apiKey != null) apiKey.addFormatter((value, offset) ->
                FormattedCharSequence.forward(revealApiKey ? value : "•".repeat(value.length()), Style.EMPTY));
    }

    @Override
    public void tick() {
        super.tick();
        if (refreshButton != null) refreshButton.active = !config().fishApiKey.isBlank()
                && controller().fishCatalogStatus().state() != TtsController.CatalogState.SYNCING;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, 7, 0xFFFFFFFF);
        TtsController.FishCatalogSnapshot status = controller().fishCatalogStatus();
        graphics.drawCenteredString(font, catalogStatus(status), width / 2, 20,
                status.state() == TtsController.CatalogState.ERROR ? 0xFFFF6B6B : 0xFFB8E986);
    }

    @Override
    public void onClose() {
        commitTextFields();
        minecraft.setScreen(parent);
    }

    private static Component selectedVoice(TtsConfig config) {
        FishVoiceModel voice = config.fishVoiceCatalog.stream()
                .filter(entry -> entry.id.equalsIgnoreCase(config.fishReferenceId)).findFirst().orElse(null);
        if (voice != null) return Component.literal(shorten(voice.name, 16));
        if (!config.fishReferenceId.isBlank()) return Component.literal(shorten(config.fishReferenceId, 16));
        return Component.translatable("gui.openyourmouth.voice.not_selected");
    }

    private static Component catalogStatus(TtsController.FishCatalogSnapshot status) {
        return switch (status.state()) {
            case NOT_CONFIGURED -> Component.translatable("gui.openyourmouth.fish.status.not_configured");
            case IDLE -> Component.translatable("gui.openyourmouth.fish.status.ready_local", config().fishVoiceCatalog.size());
            case SYNCING -> Component.translatable("gui.openyourmouth.fish.status.connecting");
            case READY -> Component.translatable("gui.openyourmouth.fish.status.ready", status.accountVoices());
            case ERROR -> Component.translatable("gui.openyourmouth.fish.status.error." + status.error().toLowerCase(Locale.ROOT));
        };
    }

    private static Component modelName(FishBaseModel model) { return Component.translatable(model.translationKey()); }
    private static Component latencyName(String value) { return Component.translatable("gui.openyourmouth.fish.latency." + value); }
    private static String percent(double value) { return Math.round(value * 100.0D) + "%"; }
    private static String shorten(String value, int maximum) { return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…"; }
    private static TtsController controller() { return OpenYourMouthClient.controller(); }
    private static TtsConfig config() { return controller().config(); }
    private static void save() { controller().saveConfig(); }
}
