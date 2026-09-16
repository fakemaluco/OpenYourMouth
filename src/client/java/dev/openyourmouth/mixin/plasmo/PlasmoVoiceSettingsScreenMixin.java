package dev.openyourmouth.mixin.plasmo;

import dev.openyourmouth.integration.plasmo.PlasmoFishTabWidget;
import dev.openyourmouth.integration.plasmo.PlasmoAddonMenuActions;
import dev.openyourmouth.OpenYourMouthClient;
import dev.openyourmouth.config.TtsConfig;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.plo.slib.api.chat.component.McTextComponent;
import su.plo.voice.client.BaseVoiceClient;
import su.plo.voice.client.config.VoiceClientConfig;
import su.plo.voice.client.gui.settings.VoiceSettingsNavigation;
import su.plo.voice.client.gui.settings.VoiceSettingsScreen;

/** Adds Fish as a native Plasmo settings tab instead of mixing it into Add-ons. */
@Mixin(value = VoiceSettingsScreen.class, remap = false)
public abstract class PlasmoVoiceSettingsScreenMixin {
    @Shadow @Final private BaseVoiceClient voiceClient;
    @Shadow @Final private VoiceClientConfig config;
    @Shadow @Final private VoiceSettingsNavigation navigation;

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lsu/plo/voice/client/gui/settings/VoiceSettingsNavigation;init()V"))
    private void openyourmouth$addFishTab(CallbackInfo ci) {
        VoiceSettingsScreen screen = (VoiceSettingsScreen) (Object) this;
        PlasmoAddonMenuActions.setSettingsScreen(screen);
        if (OpenYourMouthClient.controller().config().provider != TtsConfig.Provider.FISH_AUDIO) return;
        navigation.addTab(
                McTextComponent.translatable("gui.openyourmouth.fish.tab"),
                Identifier.fromNamespaceAndPath("openyourmouth", "textures/gui/fish.png"),
                new PlasmoFishTabWidget(screen, voiceClient, config)
        );
    }
}
