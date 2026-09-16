package dev.openyourmouth.gui;

import dev.openyourmouth.OpenYourMouthClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ConsentScreen extends Screen {
    private final Screen parent;
    private final Runnable onAccepted;
    private final boolean enableAfterConsent;

    public ConsentScreen(Screen parent) {
        this(parent, () -> {}, true);
    }

    public ConsentScreen(Screen parent, Runnable onAccepted) {
        this(parent, onAccepted, true);
    }

    public ConsentScreen(Screen parent, Runnable onAccepted, boolean enableAfterConsent) {
        super(Component.translatable("gui.openyourmouth.consent.title"));
        this.parent = parent;
        this.onAccepted = onAccepted;
        this.enableAfterConsent = enableAfterConsent;
    }

    @Override
    protected void init() {
        int y = height / 2 + 34;
        addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.consent.accept"), button -> {
            OpenYourMouthClient.controller().config().consentAccepted = true;
            if (enableAfterConsent) OpenYourMouthClient.controller().config().enabled = true;
            OpenYourMouthClient.controller().saveConfig();
            onAccepted.run();
            minecraft.setScreen(parent);
        }).bounds(width / 2 - 102, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.consent.decline"), button -> {
            OpenYourMouthClient.controller().config().consentAccepted = false;
            OpenYourMouthClient.controller().config().enabled = false;
            OpenYourMouthClient.controller().saveConfig();
            minecraft.setScreen(parent);
        }).bounds(width / 2 + 2, y, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 58, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("gui.openyourmouth.consent.line1"), width / 2, height / 2 - 28, 0xFFD0D0D0);
        graphics.drawCenteredString(font, Component.translatable("gui.openyourmouth.consent.line2"), width / 2, height / 2 - 14, 0xFFD0D0D0);
        graphics.drawCenteredString(font, Component.translatable("gui.openyourmouth.consent.line3"), width / 2, height / 2, 0xFFFFC060);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
