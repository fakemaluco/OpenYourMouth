package dev.openyourmouth.integration.simple;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;
import dev.openyourmouth.OpenYourMouthClient;

public final class OpenYourMouthSimplePlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return OpenYourMouthClient.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MergeClientSoundEvent.class, this::onMergeAudio);
    }

    private void onMergeAudio(MergeClientSoundEvent event) {
        if (!OpenYourMouthClient.usesSimple()) return;
        short[] frame = OpenYourMouthClient.controller().playback().pollFrame();
        if (frame != null) event.mergeAudio(scale(frame, OpenYourMouthClient.controller().config().volume));
    }

    private static short[] scale(short[] input, double gain) {
        if (gain == 1.0D) return input;
        short[] output = new short[input.length];
        for (int i = 0; i < input.length; i++) output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(input[i] * gain)));
        return output;
    }
}
