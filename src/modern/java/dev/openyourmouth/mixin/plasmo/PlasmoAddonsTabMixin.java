package dev.openyourmouth.mixin.plasmo;

import dev.openyourmouth.OpenYourMouthClient;
import dev.openyourmouth.integration.plasmo.PlasmoAddonMenuActions;
import dev.openyourmouth.config.TtsConfig;
import dev.openyourmouth.gui.VoicevoxSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.plo.lib.mod.client.gui.components.Button;
import su.plo.lib.mod.client.gui.components.TextFieldWidget;
import su.plo.voice.client.gui.settings.tab.AddonsTabWidget;
import su.plo.voice.client.gui.settings.tab.TabWidget;
import su.plo.slib.api.chat.component.McTextComponent;

/** Adds native Plasmo action and custom-text entries that AddonConfig cannot represent. */
@Mixin(value = AddonsTabWidget.class, remap = false)
public abstract class PlasmoAddonsTabMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void openyourmouth$addActions(CallbackInfo ci) {
        PlasmoAddonMenuActions.setOpenTab((AddonsTabWidget) (Object) this);
        TabWidget tab = (TabWidget) (Object) this;
        var config = OpenYourMouthClient.controller().config();

        if (config.customSkipCommand) {
            TextFieldWidget customSkip = new TextFieldWidget(0, 0, 124, 20,
                    McTextComponent.translatable("gui.openyourmouth.skip_custom.label"));
            customSkip.setMaxLength(32);
            customSkip.setValue(config.skipTrigger);
            customSkip.setSuggestion(Component.translatable("gui.openyourmouth.skip_trigger.hint").getString());
            customSkip.setResponder(value -> {
                if (value.isBlank()) return;
                config.skipTrigger = value.strip();
                config.customSkipCommand = true;
                OpenYourMouthClient.controller().saveConfig();
            });
            tab.addEntry(tab.new OptionEntry<>(
                    McTextComponent.translatable("gui.openyourmouth.skip_custom.label"), customSkip, null,
                    McTextComponent.translatable("gui.openyourmouth.skip_trigger.tooltip")
            ));
        }

        if (config.provider == TtsConfig.Provider.VOICEVOX) {
            String key = "gui.openyourmouth.voicevox.settings";
            Button settings = new Button(0, 0, 124, 20, McTextComponent.translatable(key), ignored -> {
                var minecraft = net.minecraft.client.Minecraft.getInstance();
                minecraft.gui.setScreen(new VoicevoxSettingsScreen(minecraft.gui.screen()));
            }, Button.NO_TOOLTIP);
            tab.addEntry(tab.new OptionEntry<>(McTextComponent.translatable(key), settings, null));
        }

        Button button = new Button(0, 0, 124, 20,
                McTextComponent.translatable("gui.openyourmouth.skip_now"),
                ignored -> OpenYourMouthClient.controller().skipCurrent(), Button.NO_TOOLTIP);
        tab.addEntry(tab.new OptionEntry<>(
                McTextComponent.translatable("gui.openyourmouth.skip_now"), button, null,
                McTextComponent.translatable("gui.openyourmouth.skip_now.tooltip")
        ));
    }
}
