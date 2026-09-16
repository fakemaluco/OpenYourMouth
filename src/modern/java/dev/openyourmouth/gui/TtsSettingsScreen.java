package dev.openyourmouth.gui;

import dev.openyourmouth.OpenYourMouthClient;
import dev.openyourmouth.TtsController;
import dev.openyourmouth.config.FishVoiceModel;
import dev.openyourmouth.config.TtsConfig;
import dev.openyourmouth.tts.EdgeVoiceCatalog;
import dev.openyourmouth.tts.SpokenLanguageOptions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Shared compact settings screen opened by both supported voice chats. */
public final class TtsSettingsScreen extends Screen {
    private static final String CUSTOM_SKIP = "__custom__";
    private static final List<String> SKIP_TRIGGERS = List.of("-skip", "!skip", ".skip", "tts-skip", CUSTOM_SKIP);

    private final Screen parent;
    private EditBox customSkipTrigger;
    private Button skipButton;
    private int top;

    public TtsSettingsScreen(Screen parent) {
        super(Component.translatable("gui.openyourmouth.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        TtsConfig config = config();
        top = Math.max(8, (height - 224) / 2);
        if (config.advancedSettings) initAdvanced(config);
        else initSimple(config);
    }

    private void initSimple(TtsConfig config) {
        int x = width / 2 - 150;
        int y = top;

        addRenderableWidget(CycleButton.onOffBuilder(config.enabled).create(x, y, 300, 20,
                Component.translatable("gui.openyourmouth.enabled.label"), (button, value) -> {
                    if (value && !config.consentAccepted) {
                        button.setValue(false);
                        OpenYourMouthClient.openConsentScreen(() -> button.setValue(true));
                    } else {
                        OpenYourMouthClient.controller().setEnabled(value);
                    }
                }));
        y += 24;

        addRenderableWidget(CycleButton.builder(TtsSettingsScreen::providerName, config.provider)
                .withValues(List.of(TtsConfig.Provider.values()))
                .create(x, y, 148, 20, Component.translatable("gui.openyourmouth.provider.label"), (button, value) -> {
                    OpenYourMouthClient.controller().setProvider(value);
                    reopen();
                }));
        if (config.provider == TtsConfig.Provider.VOICEVOX) {
            Button engineLanguage = Button.builder(Component.translatable("gui.openyourmouth.voicevox.language.engine"), ignored -> {})
                    .bounds(x + 152, y, 148, 20).build();
            engineLanguage.active = false;
            addRenderableWidget(engineLanguage);
        } else {
            addRenderableWidget(CycleButton.builder(TtsSettingsScreen::localeName, config.locale)
                    .withValues(SpokenLanguageOptions.LOCALES)
                    .create(x + 152, y, 148, 20, Component.translatable("gui.openyourmouth.locale.label"), (button, value) -> {
                        config.locale = value;
                        if (config.provider == TtsConfig.Provider.EDGE_TTS) config.edgeVoice = EdgeVoiceCatalog.selectedOrDefault(value, "");
                        save();
                        reopen();
                    }));
        }
        y += 24;

        addVoiceControl(config, x, y);
        y += 24;

        addRenderableWidget(new ValueSlider(x, y, 300, config.volume, 0.0D, 2.0D, 0.05D,
                value -> Component.translatable("gui.openyourmouth.volume", Math.round(value * 100.0D) + "%"),
                value -> { config.volume = value; save(); }));
        y += 24;

        addRenderableWidget(CycleButton.onOffBuilder(config.hearSelf).create(x, y, 148, 20,
                Component.translatable("gui.openyourmouth.hear_self.label"), (button, value) -> OpenYourMouthClient.controller().setHearSelf(value)));
        addRenderableWidget(CycleButton.onOffBuilder(config.sendChatMessages).create(x + 152, y, 148, 20,
                Component.translatable("gui.openyourmouth.send_chat.label"), (button, value) -> OpenYourMouthClient.controller().setSendChatMessages(value)));
        y += 24;

        CycleButton<Boolean> ignoreAccents = CycleButton.onOffBuilder(config.ignoreAccents).create(x, y, 148, 20,
                Component.translatable("gui.openyourmouth.ignore_accents.label"),
                (button, value) -> OpenYourMouthClient.controller().setIgnoreAccents(value));
        ignoreAccents.setTooltip(Tooltip.create(Component.translatable("gui.openyourmouth.ignore_accents.tooltip")));
        addRenderableWidget(ignoreAccents);
        CycleButton<Boolean> ignoreUppercase = CycleButton.onOffBuilder(config.ignoreUppercase).create(x + 152, y, 148, 20,
                Component.translatable("gui.openyourmouth.ignore_uppercase.label"),
                (button, value) -> OpenYourMouthClient.controller().setIgnoreUppercase(value));
        ignoreUppercase.setTooltip(Tooltip.create(Component.translatable("gui.openyourmouth.ignore_uppercase.tooltip")));
        addRenderableWidget(ignoreUppercase);
        y += 24;

        skipButton = addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.skip_now"), ignored -> OpenYourMouthClient.controller().skipCurrent())
                .bounds(x, y, 300, 20).tooltip(Tooltip.create(Component.translatable("gui.openyourmouth.skip_now.tooltip"))).build());
        y += 28;

        addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.advanced.open"), ignored -> {
            config.advancedSettings = true;
            save();
            reopen();
        }).bounds(x, y, 148, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose()).bounds(x + 152, y, 148, 20).build());
    }

    private void initAdvanced(TtsConfig config) {
        int x = width / 2 - 150;
        int y = top;

        if (OpenYourMouthClient.areBothVoiceChatsLoaded()) {
            addRenderableWidget(CycleButton.builder(TtsSettingsScreen::backendName, config.backend)
                    .withValues(List.of(TtsConfig.Backend.values()))
                    .create(x, y, 300, 20, Component.translatable("gui.openyourmouth.backend.label"), (button, value) -> {
                        config.backend = value;
                        save();
                    }));
            y += 24;
        }

        String currentSkip = config.customSkipCommand || !TtsConfig.isPresetSkipTrigger(config.skipTrigger) ? CUSTOM_SKIP : config.skipTrigger;
        addRenderableWidget(CycleButton.builder(TtsSettingsScreen::skipName, currentSkip).withValues(SKIP_TRIGGERS)
                .create(x, y, 300, 20, Component.translatable("gui.openyourmouth.skip_trigger"), (button, value) -> {
                    config.customSkipCommand = CUSTOM_SKIP.equals(value);
                    if (!config.customSkipCommand) config.skipTrigger = value;
                    save();
                    reopen();
                }));
        y += 24;

        if (CUSTOM_SKIP.equals(currentSkip)) {
            customSkipTrigger = new EditBox(font, x, y, 300, 20, Component.translatable("gui.openyourmouth.skip_custom.label"));
            customSkipTrigger.setMaxLength(32);
            customSkipTrigger.setValue(config.skipTrigger);
            customSkipTrigger.setHint(Component.translatable("gui.openyourmouth.skip_trigger.hint"));
            addRenderableWidget(customSkipTrigger);
            y += 24;
        }

        if (config.provider == TtsConfig.Provider.EDGE_TTS) {
            addRenderableWidget(new ValueSlider(x, y, 300, config.speakingRate, 0.5D, 2.0D, 0.05D,
                    value -> Component.translatable("gui.openyourmouth.rate", Math.round(value * 100.0D) + "%"),
                    value -> { config.speakingRate = value; save(); }));
            y += 24;
        } else if (config.provider == TtsConfig.Provider.FISH_AUDIO) {
            addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.fish.settings"), ignored ->
                    minecraft.gui.setScreen(new FishSettingsScreen(this))).bounds(x, y, 300, 20).build());
            y += 24;
        } else if (config.provider == TtsConfig.Provider.VOICEVOX) {
            addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.voicevox.settings"), ignored ->
                    minecraft.gui.setScreen(new VoicevoxSettingsScreen(this))).bounds(x, y, 300, 20).build());
            y += 24;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.advanced.close"), ignored -> {
            saveCustomSkip();
            config.advancedSettings = false;
            save();
            reopen();
        }).bounds(x, y, 148, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose()).bounds(x + 152, y, 148, 20).build());
    }

    private void addVoiceControl(TtsConfig config, int x, int y) {
        if (config.provider == TtsConfig.Provider.EDGE_TTS) {
            EdgeVoiceCatalog.refreshAsync();
            List<String> voices = EdgeVoiceCatalog.forLocale(config.locale);
            config.edgeVoice = EdgeVoiceCatalog.selectedOrDefault(config.locale, config.edgeVoice);
            addRenderableWidget(CycleButton.builder(Component::literal, config.edgeVoice).withValues(voices)
                    .create(x, y, 300, 20, Component.translatable("gui.openyourmouth.voice"), (button, value) -> {
                        config.edgeVoice = value;
                        save();
                    }));
        } else if (config.provider == TtsConfig.Provider.FISH_AUDIO) {
            addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.voice", selectedFishVoice(config)), ignored ->
                    minecraft.gui.setScreen(new FishSettingsScreen(this))).bounds(x, y, 300, 20).build());
        } else if (config.provider == TtsConfig.Provider.VOICEVOX) {
            addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.voice", selectedVoicevoxVoice(config)), ignored ->
                    minecraft.gui.setScreen(new VoicevoxSettingsScreen(this))).bounds(x, y, 300, 20).build());
        } else {
            Button automatic = Button.builder(Component.translatable("gui.openyourmouth.voice.automatic"), ignored -> {})
                    .bounds(x, y, 300, 20).build();
            automatic.active = false;
            addRenderableWidget(automatic);
        }
    }

    @Override
    public void tick() {
        super.tick();
        TtsController.StatusSnapshot status = OpenYourMouthClient.controller().status();
        if (skipButton != null) skipButton.active = status.activity() == TtsController.Activity.SYNTHESIZING
                || status.activity() == TtsController.Activity.PLAYING;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 7, 0xFFFFFFFF);
        TtsController.StatusSnapshot status = OpenYourMouthClient.controller().status();
        graphics.centeredText(font, statusText(status), width / 2, 19,
                status.activity() == TtsController.Activity.ERROR ? 0xFFFF6B6B : 0xFFB8E986);
    }

    @Override
    public void onClose() {
        saveCustomSkip();
        save();
        minecraft.gui.setScreen(parent);
    }

    private void saveCustomSkip() {
        if (customSkipTrigger != null && !customSkipTrigger.getValue().isBlank()) {
            config().skipTrigger = customSkipTrigger.getValue().strip();
            config().customSkipCommand = true;
        }
    }

    private void reopen() { minecraft.gui.setScreen(new TtsSettingsScreen(parent)); }
    private static TtsConfig config() { return OpenYourMouthClient.controller().config(); }
    private static void save() { OpenYourMouthClient.controller().saveConfig(); }

    private static Component providerName(TtsConfig.Provider value) {
        return Component.translatable(switch (value) {
            case GOOGLE_TRANSLATE -> "gui.openyourmouth.provider.translate";
            case EDGE_TTS -> "gui.openyourmouth.provider.edge";
            case FISH_AUDIO -> "gui.openyourmouth.provider.fish";
            case VOICEVOX -> "gui.openyourmouth.provider.voicevox";
        });
    }

    private static Component backendName(TtsConfig.Backend value) {
        return Component.translatable(switch (value) {
            case AUTO -> "gui.openyourmouth.backend.auto";
            case SIMPLE_VOICE_CHAT -> "gui.openyourmouth.backend.simple";
            case PLASMO_VOICE -> "gui.openyourmouth.backend.plasmo";
        });
    }

    private static Component localeName(String value) {
        return Component.translatable("locale.openyourmouth." + value.replace('-', '_').toLowerCase(Locale.ROOT));
    }

    private static Component skipName(String value) {
        return CUSTOM_SKIP.equals(value) ? Component.translatable("gui.openyourmouth.skip_trigger.custom") : Component.literal(value);
    }

    private static Component selectedFishVoice(TtsConfig config) {
        FishVoiceModel voice = config.fishVoiceCatalog.stream().filter(entry -> entry.id.equalsIgnoreCase(config.fishReferenceId)).findFirst().orElse(null);
        if (voice != null) return Component.literal(voice.name);
        if (!config.fishReferenceId.isBlank()) return Component.literal(shorten(config.fishReferenceId, 28));
        return Component.translatable("gui.openyourmouth.voice.not_selected");
    }

    private static Component selectedVoicevoxVoice(TtsConfig config) {
        return config.voicevoxStyleName.isBlank() ? Component.literal("#" + config.voicevoxStyleId)
                : Component.literal(shorten(config.voicevoxStyleName, 28));
    }

    private static Component statusText(TtsController.StatusSnapshot status) {
        Component base = Component.translatable("gui.openyourmouth.status." + status.activity().name().toLowerCase(Locale.ROOT));
        if (status.queued() > 0) return Component.translatable("gui.openyourmouth.status.queued", base, status.queued());
        if (status.activity() == TtsController.Activity.ERROR && !status.error().isBlank()) {
            return Component.translatable("gui.openyourmouth.status.error.detail", shorten(status.error(), 70));
        }
        return base;
    }

    private static String format(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static String shorten(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
    }
}
