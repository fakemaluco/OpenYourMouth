package dev.openyourmouth.gui;

import dev.openyourmouth.OpenYourMouthClient;
import dev.openyourmouth.TtsController;
import dev.openyourmouth.config.TtsConfig;
import dev.openyourmouth.tts.VoicevoxStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** VOICEVOX endpoint, style catalog, and AudioQuery controls. */
public final class VoicevoxSettingsScreen extends Screen {
    private final Screen parent;
    private CommitEditBox endpoint;
    private Button voiceButton;
    private Button refreshButton;

    public VoicevoxSettingsScreen(Screen parent) {
        super(Component.translatable("gui.openyourmouth.voicevox.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        TtsConfig config = config();
        int x = width / 2 - 150;
        int y = Math.max(34, (height - 194) / 2);

        endpoint = new CommitEditBox(font, x, y, 300, Component.translatable("gui.openyourmouth.voicevox.endpoint"),
                config.voicevoxEndpoint, 512, value -> {
            config.voicevoxEndpoint = value.isBlank() ? TtsConfig.DEFAULT_VOICEVOX_ENDPOINT : value.strip();
            save();
            refresh();
        });
        endpoint.setHint(Component.translatable("gui.openyourmouth.voicevox.endpoint.hint"));
        addRenderableWidget(endpoint);
        y += 24;

        voiceButton = addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.voice", selectedVoice(config)), ignored -> openVoices())
                .bounds(x, y, 224, 20).build());
        refreshButton = addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.voicevox.refresh"), ignored -> {
            endpoint.commitNow();
            refresh();
        }).bounds(x + 228, y, 72, 20).build());
        y += 28;

        addRenderableWidget(new ValueSlider(x, y, 300, config.voicevoxSpeed, 0.5D, 2.0D, 0.05D,
                value -> Component.translatable("gui.openyourmouth.voicevox.speed", percent(value)),
                value -> { config.voicevoxSpeed = value; save(); }));
        y += 24;
        addRenderableWidget(new ValueSlider(x, y, 300, config.voicevoxPitch, -0.15D, 0.15D, 0.01D,
                value -> Component.translatable("gui.openyourmouth.voicevox.pitch", signed(value)),
                value -> { config.voicevoxPitch = value; save(); }));
        y += 24;
        addRenderableWidget(new ValueSlider(x, y, 300, config.voicevoxIntonation, 0.0D, 2.0D, 0.05D,
                value -> Component.translatable("gui.openyourmouth.voicevox.intonation", percent(value)),
                value -> { config.voicevoxIntonation = value; save(); }));
        y += 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose()).bounds(x, y, 300, 20).build());

        if (controller().voicevoxCatalogStatus().state() == TtsController.CatalogState.IDLE
                && controller().voicevoxCatalogStatus().styles().isEmpty()) refresh();
    }

    private void refresh() {
        controller().refreshVoicevoxCatalog().whenComplete((ignored, error) -> minecraft.execute(() -> {
            if (minecraft.gui.screen() == this) rebuildWidgets();
        }));
    }

    private void openVoices() {
        endpoint.commitNow();
        List<VoicevoxStyle> styles = controller().voicevoxCatalogStatus().styles();
        if (styles.isEmpty()) return;
        List<FishOptionSelectionScreen.Choice> choices = styles.stream()
                .map(style -> new FishOptionSelectionScreen.Choice(Integer.toString(style.id()), Component.literal(style.name()))).toList();
        minecraft.gui.setScreen(new FishOptionSelectionScreen(this, Component.translatable("gui.openyourmouth.voicevox.voice.select"),
                choices, Integer.toString(config().voicevoxStyleId), value -> {
            int id = Integer.parseInt(value);
            VoicevoxStyle selected = styles.stream().filter(style -> style.id() == id).findFirst().orElseThrow();
            config().voicevoxStyleId = selected.id();
            config().voicevoxStyleName = selected.name();
            save();
        }));
    }

    @Override
    public void tick() {
        super.tick();
        TtsController.VoicevoxCatalogSnapshot status = controller().voicevoxCatalogStatus();
        if (voiceButton != null) voiceButton.active = !status.styles().isEmpty();
        if (refreshButton != null) refreshButton.active = status.state() != TtsController.CatalogState.SYNCING;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 7, 0xFFFFFFFF);
        var status = controller().voicevoxCatalogStatus();
        Component text = !status.loopback()
                ? Component.translatable("gui.openyourmouth.voicevox.status.remote")
                : switch (status.state()) {
                    case SYNCING -> Component.translatable("gui.openyourmouth.voicevox.status.connecting");
                    case READY -> Component.translatable("gui.openyourmouth.voicevox.status.ready", status.styles().size());
                    case ERROR -> Component.translatable("gui.openyourmouth.voicevox.status.error");
                    default -> Component.translatable("gui.openyourmouth.voicevox.status.idle");
                };
        int color = !status.loopback() ? 0xFFFFC060 : status.state() == TtsController.CatalogState.ERROR ? 0xFFFF6B6B : 0xFFB8E986;
        graphics.centeredText(font, text, width / 2, 20, color);
    }

    @Override
    public void onClose() {
        if (endpoint != null) endpoint.commitNow();
        minecraft.gui.setScreen(parent);
    }

    private static Component selectedVoice(TtsConfig config) {
        return config.voicevoxStyleName.isBlank() ? Component.literal("#" + config.voicevoxStyleId) : Component.literal(shorten(config.voicevoxStyleName, 34));
    }
    private static String percent(double value) { return Math.round(value * 100.0D) + "%"; }
    private static String signed(double value) { return String.format(Locale.ROOT, "%+.2f", value); }
    private static String shorten(String value, int maximum) { return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…"; }
    private static TtsController controller() { return OpenYourMouthClient.controller(); }
    private static TtsConfig config() { return controller().config(); }
    private static void save() { controller().saveConfig(); }
}
