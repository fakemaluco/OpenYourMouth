package dev.openyourmouth.integration.plasmo;

import dev.openyourmouth.OpenYourMouthClient;
import dev.openyourmouth.audio.PcmAudio;
import dev.openyourmouth.config.TtsConfig;
import dev.openyourmouth.tts.EdgeVoiceCatalog;
import dev.openyourmouth.tts.SpokenLanguageOptions;
import su.plo.config.entry.DoubleConfigEntry;
import net.minecraft.client.Minecraft;
import su.plo.config.entry.BooleanConfigEntry;
import su.plo.config.entry.IntConfigEntry;
import su.plo.slib.api.chat.component.McTextComponent;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.config.addon.AddonConfig;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureEvent;
import su.plo.voice.api.client.event.render.HudActivationRenderEvent;
import su.plo.voice.api.client.event.render.VoiceDistanceRenderEvent;
import su.plo.voice.api.event.EventPriority;

import java.util.List;

@Addon(id = "openyourmouth", name = "addon.openyourmouth.name", version = "1.6.1", authors = {"Fakiz_", "OpenYourMouth contributors"}, scope = AddonLoaderScope.CLIENT)
public final class OpenYourMouthPlasmoAddon implements AddonInitializer {
    private static final List<String> SKIP_CHOICES = List.of("-skip", "!skip", ".skip", "tts-skip", "gui.openyourmouth.skip_trigger.custom");
    @InjectPlasmoVoice
    private PlasmoVoiceClient voiceClient;
    private volatile long hideTtsVisualsUntil;

    @Override
    public void onAddonInitialize() {
        voiceClient.getConfig().getAdvanced().getVisualizeVoiceDistance().set(false);
        registerMenu();
        voiceClient.getEventBus().register(this, AudioCaptureEvent.class, EventPriority.NORMAL, this::onCapture);
        voiceClient.getEventBus().register(this, VoiceDistanceRenderEvent.class, EventPriority.HIGHEST, this::onDistanceRender);
        voiceClient.getEventBus().register(this, HudActivationRenderEvent.class, EventPriority.HIGHEST, this::onActivationRender);
    }

    private void onDistanceRender(VoiceDistanceRenderEvent event) {
        if (shouldHideTtsVisuals()) event.setCancelled(true);
    }

    private void onActivationRender(HudActivationRenderEvent event) {
        if (shouldHideTtsVisuals() && event.getActivation().isProximity()) {
            event.setRender(false);
            event.setCancelled(true);
        }
    }

    private boolean shouldHideTtsVisuals() {
        return OpenYourMouthClient.usesPlasmo() && (OpenYourMouthClient.controller().playback().isPlaying()
                || System.currentTimeMillis() < hideTtsVisualsUntil);
    }

    private void onCapture(AudioCaptureEvent event) {
        if (!OpenYourMouthClient.usesPlasmo()) return;
        short[] captured = event.getSamples();
        int channels = Math.max(1, event.getDevice().getFormat().getChannels());
        int outputSamplesPerChannel = Math.max(1, captured.length / channels);
        int captureRate = Math.max(1, Math.round(event.getDevice().getFormat().getSampleRate()));
        int sourceSamples = Math.max(1, (int) Math.round(outputSamplesPerChannel * (PcmAudio.SAMPLE_RATE / (double) captureRate)));
        short[] mono = OpenYourMouthClient.controller().playback().pollSamples(sourceSamples);
        if (mono == null) return;
        hideTtsVisualsUntil = System.currentTimeMillis() + 1_500L;
        if (captureRate != PcmAudio.SAMPLE_RATE || mono.length != outputSamplesPerChannel) mono = PcmAudio.resample(mono, PcmAudio.SAMPLE_RATE, captureRate);
        if (mono.length != outputSamplesPerChannel) mono = java.util.Arrays.copyOf(mono, outputSamplesPerChannel);

        short[] frame = mono;
        if (channels > 1) {
            frame = new short[captured.length];
            for (int sample = 0; sample < outputSamplesPerChannel; sample++) {
                for (int channel = 0; channel < channels; channel++) frame[sample * channels + channel] = mono[sample];
            }
        }
        PcmAudio.mixInto(captured, frame, OpenYourMouthClient.controller().config().volume, 0.65D);
    }

    private void registerMenu() {
        TtsConfig core = OpenYourMouthClient.controller().config();
        AddonConfig menu = voiceClient.getAddonConfig(this);
        BooleanConfigEntry enabled = menu.addToggle("tts_enabled", text("gui.openyourmouth.enabled.label"), text("gui.openyourmouth.enabled.tooltip"), core.enabled);
        BooleanConfigEntry hearSelf = menu.addToggle("hear_self", text("gui.openyourmouth.hear_self.label"), text("gui.openyourmouth.hear_self.tooltip"), core.hearSelf);
        BooleanConfigEntry sendChat = menu.addToggle("send_chat_messages", text("gui.openyourmouth.send_chat.label"), text("gui.openyourmouth.send_chat.tooltip"), core.sendChatMessages);
        BooleanConfigEntry ignoreAccents = menu.addToggle("ignore_accents", text("gui.openyourmouth.ignore_accents.label"), text("gui.openyourmouth.ignore_accents.tooltip"), core.ignoreAccents);
        BooleanConfigEntry ignoreUppercase = menu.addToggle("ignore_uppercase", text("gui.openyourmouth.ignore_uppercase.label"), text("gui.openyourmouth.ignore_uppercase.tooltip"), core.ignoreUppercase);
        DoubleConfigEntry volume = menu.addVolumeSlider("tts_volume", text("gui.openyourmouth.volume.label"), null, "%", core.volume, 0.0D, 2.0D);
        IntConfigEntry provider = menu.addDropDown("tts_provider", text("gui.openyourmouth.provider.label"), null,
                List.of("gui.openyourmouth.provider.translate", "gui.openyourmouth.provider.edge", "gui.openyourmouth.provider.fish",
                        "gui.openyourmouth.provider.voicevox"), false, core.provider.ordinal());
        int localeIndex = Math.max(0, SpokenLanguageOptions.LOCALES.indexOf(core.locale));
        IntConfigEntry locale = menu.addDropDown("tts_language", text("gui.openyourmouth.locale.label"), null,
                SpokenLanguageOptions.LOCALES.stream().map(value -> "locale.openyourmouth." + value.replace('-', '_').toLowerCase()).toList(), false, localeIndex);
        int skipIndex = core.customSkipCommand || !TtsConfig.isPresetSkipTrigger(core.skipTrigger)
                ? SKIP_CHOICES.size() - 1 : Math.max(0, SKIP_CHOICES.indexOf(core.skipTrigger));
        IntConfigEntry skip = menu.addDropDown("tts_skip_command", text("gui.openyourmouth.skip_trigger"), text("gui.openyourmouth.skip_trigger.tooltip"),
                SKIP_CHOICES, false, skipIndex);
        IntConfigEntry backend = null;
        if (OpenYourMouthClient.areBothVoiceChatsLoaded()) {
            backend = menu.addDropDown("tts_backend", text("gui.openyourmouth.backend.label"), null,
                    List.of("gui.openyourmouth.backend.auto", "gui.openyourmouth.backend.simple", "gui.openyourmouth.backend.plasmo"),
                    false, core.backend.ordinal());
        }

        enabled.set(core.enabled);
        hearSelf.set(core.hearSelf);
        sendChat.set(core.sendChatMessages);
        ignoreAccents.set(core.ignoreAccents);
        ignoreUppercase.set(core.ignoreUppercase);
        volume.set(core.volume);
        provider.set(core.provider.ordinal());
        locale.set(localeIndex);
        skip.set(skipIndex);
        if (backend != null) backend.set(core.backend.ordinal());

        enabled.addChangeListener(value -> {
            if (value && !core.consentAccepted) {
                core.enabled = false;
                enabled.set(false);
                OpenYourMouthClient.openConsentScreen(() -> enabled.set(true));
            } else {
                core.enabled = value;
                save();
            }
        });
        hearSelf.addChangeListener(value -> { core.hearSelf = value; save(); });
        sendChat.addChangeListener(value -> { core.sendChatMessages = value; save(); });
        ignoreAccents.addChangeListener(value -> OpenYourMouthClient.controller().setIgnoreAccents(value));
        ignoreUppercase.addChangeListener(value -> OpenYourMouthClient.controller().setIgnoreUppercase(value));
        volume.addChangeListener(value -> { core.volume = value; save(); });
        provider.addChangeListener(value -> {
            OpenYourMouthClient.controller().setProvider(TtsConfig.Provider.values()[value]);
            installProviderWidgets(menu, core);
            save();
            Minecraft.getInstance().execute(PlasmoAddonMenuActions::refreshSettings);
        });
        locale.addChangeListener(value -> {
            core.locale = SpokenLanguageOptions.LOCALES.get(value);
            if (core.provider == TtsConfig.Provider.EDGE_TTS) {
                core.edgeVoice = EdgeVoiceCatalog.selectedOrDefault(core.locale, "");
                installProviderWidgets(menu, core);
            }
            save();
            Minecraft.getInstance().execute(PlasmoAddonMenuActions::refreshSettings);
        });
        skip.addChangeListener(value -> {
            core.customSkipCommand = value == SKIP_CHOICES.size() - 1;
            if (!core.customSkipCommand) core.skipTrigger = SKIP_CHOICES.get(value);
            save();
            Minecraft.getInstance().execute(PlasmoAddonMenuActions::refreshOpenTab);
        });
        if (backend != null) backend.addChangeListener(value -> {
            core.backend = TtsConfig.Backend.values()[value];
            save();
        });

        installProviderWidgets(menu, core);
    }

    private void installProviderWidgets(AddonConfig menu, TtsConfig core) {
        menu.removeWidget("edge_voice");
        menu.removeWidget("edge_rate");
        menu.removeWidget("edge_pitch");
        if (core.provider != TtsConfig.Provider.EDGE_TTS) return;

        List<String> voices = EdgeVoiceCatalog.forLocale(core.locale);
        core.edgeVoice = EdgeVoiceCatalog.selectedOrDefault(core.locale, core.edgeVoice);
        IntConfigEntry voice = menu.addDropDown("edge_voice", text("gui.openyourmouth.edge_voice"), text("gui.openyourmouth.edge_voice.tooltip"),
                voices, false, Math.max(0, voices.indexOf(core.edgeVoice)));
        DoubleConfigEntry rate = menu.addVolumeSlider("edge_rate", text("gui.openyourmouth.rate.label"), null, "%", core.speakingRate, 0.5D, 2.0D);
        voice.addChangeListener(value -> { core.edgeVoice = voices.get(value); save(); });
        rate.addChangeListener(value -> { core.speakingRate = value; save(); });
    }

    private void save() { OpenYourMouthClient.controller().saveConfig(); }
    private static McTextComponent text(String key) { return McTextComponent.translatable(key); }
}
