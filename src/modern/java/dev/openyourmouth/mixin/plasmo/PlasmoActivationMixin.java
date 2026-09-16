package dev.openyourmouth.mixin.plasmo;

import dev.openyourmouth.OpenYourMouthClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import su.plo.voice.client.audio.capture.VoiceClientActivation;

@Mixin(value = VoiceClientActivation.class, remap = false)
public abstract class PlasmoActivationMixin {
    @Shadow private boolean active;
    @Shadow private long lastActivation;

    @Inject(method = "process", at = @At("HEAD"), cancellable = true)
    private void openyourmouth$forceTts(short[] samples, ClientActivation.Result parentResult, CallbackInfoReturnable<ClientActivation.Result> cir) {
        ClientActivation self = (ClientActivation) (Object) this;
        if (OpenYourMouthClient.usesPlasmo() && OpenYourMouthClient.controller().playback().isPlaying() && self.isProximity()) {
            active = true;
            lastActivation = System.currentTimeMillis();
            cir.setReturnValue(ClientActivation.Result.ACTIVATED);
        }
    }
}
