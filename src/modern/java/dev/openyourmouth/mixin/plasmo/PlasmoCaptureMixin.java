package dev.openyourmouth.mixin.plasmo;

import dev.openyourmouth.OpenYourMouthClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import su.plo.config.entry.BooleanConfigEntry;
import su.plo.voice.client.audio.capture.VoiceAudioCapture;

@Mixin(value = VoiceAudioCapture.class, remap = false)
public abstract class PlasmoCaptureMixin {
    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lsu/plo/config/entry/BooleanConfigEntry;value()Ljava/lang/Object;", ordinal = 0))
    private Object openyourmouth$ignoreLocalMute(BooleanConfigEntry entry) {
        if (OpenYourMouthClient.usesPlasmo() && OpenYourMouthClient.controller().playback().isPlaying()) return false;
        return entry.value();
    }
}
