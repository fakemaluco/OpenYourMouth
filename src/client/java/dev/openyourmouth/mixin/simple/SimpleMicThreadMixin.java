package dev.openyourmouth.mixin.simple;

import de.maxhenkel.voicechat.voice.client.MicThread;
import dev.openyourmouth.OpenYourMouthClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MicThread.class, remap = false)
public abstract class SimpleMicThreadMixin {
    @Shadow private boolean microphoneLocked;

    @Redirect(method = "run", at = @At(value = "FIELD", target = "Lde/maxhenkel/voicechat/voice/client/MicThread;microphoneLocked:Z"))
    private boolean openyourmouth$ignoreLocalMute(MicThread thread) {
        return OpenYourMouthClient.controller().playback().isPlaying() ? false : microphoneLocked;
    }
}
