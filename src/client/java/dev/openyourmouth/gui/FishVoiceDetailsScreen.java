package dev.openyourmouth.gui;

import dev.openyourmouth.OpenYourMouthClient;
import dev.openyourmouth.config.FishVoiceModel;
import dev.openyourmouth.config.TtsConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Details editor for manual voices and read-only ID viewer for account voices. */
public final class FishVoiceDetailsScreen extends Screen {
    private final Screen parent;
    private final FishVoiceModel voice;
    private EditBox name;
    private EditBox id;
    private Component status = Component.empty();
    private int top;

    public FishVoiceDetailsScreen(Screen parent, FishVoiceModel voice) {
        super(Component.translatable(voice == null ? "gui.openyourmouth.fish.catalog.add_title" : "gui.openyourmouth.fish.catalog.details_title"));
        this.parent = parent;
        this.voice = voice;
    }

    @Override
    protected void init() {
        int x = width / 2 - 150;
        top = Math.max(34, height / 2 - 70);
        boolean editable = voice == null || voice.source == FishVoiceModel.Source.MANUAL;

        name = new EditBox(font, x, top + 12, 300, 20, Component.translatable("gui.openyourmouth.fish.catalog.name"));
        name.setMaxLength(80);
        name.setValue(voice == null ? "" : voice.name);
        name.setHint(Component.translatable("gui.openyourmouth.fish.catalog.name.hint"));
        name.setEditable(editable);
        addRenderableWidget(name);

        id = new EditBox(font, x, top + 44, 300, 20, Component.translatable("gui.openyourmouth.fish.reference_id"));
        id.setMaxLength(128);
        id.setValue(voice == null ? "" : voice.id);
        id.setHint(Component.translatable("gui.openyourmouth.fish.reference_id.hint"));
        id.setEditable(editable);
        addRenderableWidget(id);

        int actions = top + 72;
        if (editable) {
            addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.fish.catalog.save"), ignored -> saveVoice())
                    .bounds(x, actions, 96, 20).build());
            Button remove = Button.builder(Component.translatable("gui.openyourmouth.fish.catalog.remove"), ignored -> removeVoice())
                    .bounds(x + 102, actions, 96, 20).build();
            remove.active = voice != null;
            addRenderableWidget(remove);
        } else {
            addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.fish.catalog.select"), ignored -> useVoice())
                    .bounds(x, actions, 96, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.fish.catalog.copy_id"), ignored -> copyId())
                    .bounds(x + 102, actions, 96, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose()).bounds(x + 204, actions, 96, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        int x = width / 2 - 150;
        graphics.drawString(font, Component.translatable("gui.openyourmouth.fish.catalog.name"), x, top + 2, 0xFFB0B0B0, false);
        graphics.drawString(font, Component.translatable("gui.openyourmouth.fish.reference_id"), x, top + 34, 0xFFB0B0B0, false);
        if (!status.getString().isBlank()) graphics.drawCenteredString(font, status, width / 2, top + 98, 0xFFFFD54F);
    }

    private void saveVoice() {
        String valueId = id.getValue().strip();
        if (valueId.isBlank()) {
            status = Component.translatable("gui.openyourmouth.fish.catalog.id_required");
            return;
        }
        String valueName = name.getValue().strip();
        if (valueName.isBlank()) valueName = valueId;
        TtsConfig config = config();
        if (voice != null) config.fishVoiceCatalog.removeIf(entry -> entry == voice || entry.id.equalsIgnoreCase(voice.id));
        String finalName = valueName;
        config.fishVoiceCatalog.removeIf(entry -> entry.id.equalsIgnoreCase(valueId) && entry.source == FishVoiceModel.Source.MANUAL);
        config.fishVoiceCatalog.add(new FishVoiceModel(finalName, valueId, FishVoiceModel.Source.MANUAL));
        config.fishReferenceId = valueId;
        save();
        onClose();
    }

    private void removeVoice() {
        if (voice == null || voice.source != FishVoiceModel.Source.MANUAL) return;
        config().fishVoiceCatalog.removeIf(entry -> entry == voice || entry.id.equalsIgnoreCase(voice.id));
        if (config().fishReferenceId.equalsIgnoreCase(voice.id)) config().fishReferenceId = "";
        save();
        onClose();
    }

    private void useVoice() {
        if (voice == null) return;
        config().fishReferenceId = voice.id;
        save();
        onClose();
    }

    private void copyId() {
        minecraft.keyboardHandler.setClipboard(id.getValue());
        status = Component.translatable("gui.openyourmouth.fish.catalog.copied");
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
    private static TtsConfig config() { return OpenYourMouthClient.controller().config(); }
    private static void save() { OpenYourMouthClient.controller().saveConfig(); }
}
