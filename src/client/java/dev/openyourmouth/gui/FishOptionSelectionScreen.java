package dev.openyourmouth.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/** Small direct-selection list used for Fish model and latency choices. */
final class FishOptionSelectionScreen extends Screen {
    record Choice(String value, Component label) {}

    private final Screen parent;
    private final List<Choice> choices;
    private final String current;
    private final Consumer<String> selected;
    private ChoiceList list;
    private Button useButton;

    FishOptionSelectionScreen(Screen parent, Component title, List<Choice> choices, String current,
                              Consumer<String> selected) {
        super(title);
        this.parent = parent;
        this.choices = choices;
        this.current = current;
        this.selected = selected;
    }

    @Override
    protected void init() {
        list = new ChoiceList(width, Math.max(60, height - 64), 24, 24);
        addRenderableWidget(list);
        list.setChoices(choices);
        list.children().stream().filter(entry -> entry.choice.value().equals(current)).findFirst().ifPresent(list::setSelected);
        int x = width / 2 - 150;
        useButton = addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.fish.option.select"), ignored -> use())
                .bounds(x, height - 24, 148, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(x + 152, height - 24, 148, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (useButton != null) useButton.active = list != null && list.getSelected() != null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, 7, 0xFFFFFFFF);
    }

    private void use() {
        ChoiceEntry entry = list.getSelected();
        if (entry == null) return;
        selected.accept(entry.choice.value());
        onClose();
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    private final class ChoiceList extends ObjectSelectionList<ChoiceEntry> {
        ChoiceList(int width, int height, int y, int entryHeight) {
            super(FishOptionSelectionScreen.this.minecraft, width, height, y, entryHeight);
        }

        void setChoices(List<Choice> choices) { replaceEntries(choices.stream().map(ChoiceEntry::new).toList()); }

        @Override public int getRowWidth() { return Math.min(300, FishOptionSelectionScreen.this.width - 24); }
        @Override protected int scrollBarX() { return FishOptionSelectionScreen.this.width / 2 + getRowWidth() / 2 + 4; }
    }

    private final class ChoiceEntry extends ObjectSelectionList.Entry<ChoiceEntry> {
        private final Choice choice;

        ChoiceEntry(Choice choice) { this.choice = choice; }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            int color = choice.value().equals(current) ? 0xFF9BE564 : 0xFFFFFFFF;
            graphics.drawCenteredString(font, choice.label(), getContentX() + getContentWidth() / 2, getContentY() + 7, color);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            list.setSelected(this);
            if (doubleClick) use();
            return true;
        }

        @Override public Component getNarration() { return choice.label(); }
    }
}
