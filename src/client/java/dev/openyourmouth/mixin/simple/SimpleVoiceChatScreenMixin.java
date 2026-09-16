package dev.openyourmouth.mixin.simple;

import de.maxhenkel.voicechat.gui.VoiceChatScreen;
import de.maxhenkel.voicechat.gui.VoiceChatScreenBase;
import dev.openyourmouth.gui.TtsSettingsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VoiceChatScreen.class, remap = false)
public abstract class SimpleVoiceChatScreenMixin extends VoiceChatScreenBase {
    protected SimpleVoiceChatScreenMixin() {
        super(Component.empty(), 195, 76);
    }

    @Inject(method = "init", at = @At("TAIL"), remap = true)
    private void openyourmouth$addButton(CallbackInfo ci) {
        addRenderableWidget(Button.builder(Component.literal("TTS"), button ->
                minecraft.setScreen(new TtsSettingsScreen((VoiceChatScreen) (Object) this))
        ).bounds(guiLeft + xSize / 2 - 15, guiTop + 21, 30, 20).build());
    }
}
