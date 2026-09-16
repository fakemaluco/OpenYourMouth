package dev.openyourmouth.mixin;

import dev.openyourmouth.OpenYourMouthClient;
import dev.openyourmouth.TtsController;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    protected ChatScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void openyourmouth$addChatButtons(CallbackInfo ci) {
        TtsController controller = OpenYourMouthClient.controller();
        int x = width - 50;
        int y = height - 96;

        openyourmouth$addToggle(x, y, "gui.openyourmouth.chat_button.tts",
                "gui.openyourmouth.enabled.label", () -> controller.config().enabled, controller::toggleEnabled);
        openyourmouth$addToggle(x, y + 22, "gui.openyourmouth.chat_button.send",
                "gui.openyourmouth.send_chat.label", () -> controller.config().sendChatMessages,
                controller::toggleSendChatMessages);
        openyourmouth$addToggle(x, y + 44, "gui.openyourmouth.chat_button.hear",
                "gui.openyourmouth.hear_self.label", () -> controller.config().hearSelf,
                controller::toggleHearSelf);
    }

    @Unique
    private void openyourmouth$addToggle(int x, int y, String shortLabel, String tooltipLabel,
                                         BooleanSupplier enabled, Runnable toggle) {
        Button button = Button.builder(Component.translatable(shortLabel), pressed -> {
            toggle.run();
            openyourmouth$updateButton(pressed, shortLabel, tooltipLabel, enabled.getAsBoolean());
        }).bounds(x, y, 44, 18).build();
        openyourmouth$updateButton(button, shortLabel, tooltipLabel, enabled.getAsBoolean());
        addRenderableWidget(button);
    }

    @Unique
    private static void openyourmouth$updateButton(Button button, String shortLabel, String tooltipLabel,
                                                    boolean enabled) {
        button.setMessage(Component.translatable(shortLabel)
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
        button.setTooltip(Tooltip.create(Component.translatable(tooltipLabel)
                .append(": ").append(Component.translatable(enabled ? "options.on" : "options.off"))));
    }
}
