package dev.openyourmouth.gui;

import dev.openyourmouth.OpenYourMouthClient;
import dev.openyourmouth.TtsController;
import dev.openyourmouth.config.FishVoiceModel;
import dev.openyourmouth.config.TtsConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Searchable Fish voice catalog with distinct account and manually saved entries. */
public final class FishVoiceCatalogScreen extends Screen {
    private final Screen parent;
    private EditBox search;
    private VoiceList voiceList;
    private Button selectButton;
    private Button detailsButton;
    private Button refreshButton;

    public FishVoiceCatalogScreen(Screen parent) {
        super(Component.translatable("gui.openyourmouth.fish.catalog.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 150;
        search = new EditBox(font, x, 22, 300, 20, Component.translatable("gui.openyourmouth.fish.catalog.search"));
        search.setHint(Component.translatable("gui.openyourmouth.fish.catalog.search"));
        search.setMaxLength(80);
        search.setResponder(ignored -> rebuildList());
        addRenderableWidget(search);

        voiceList = new VoiceList(width, Math.max(60, height - 96), 46, 30);
        addRenderableWidget(voiceList);
        rebuildList();

        int actionsY = height - 46;
        selectButton = addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.fish.catalog.select"), ignored -> selectVoice())
                .bounds(x, actionsY, 96, 20).build());
        detailsButton = addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.fish.catalog.details"), ignored -> openDetails())
                .bounds(x + 102, actionsY, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.fish.catalog.add_short"), ignored ->
                minecraft.setScreen(new FishVoiceDetailsScreen(this, null))).bounds(x + 204, actionsY, 96, 20).build());

        refreshButton = addRenderableWidget(Button.builder(Component.translatable("gui.openyourmouth.fish.refresh"), ignored -> refresh())
                .bounds(x, height - 22, 148, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose()).bounds(x + 152, height - 22, 148, 20).build());

        TtsController.FishCatalogSnapshot status = controller().fishCatalogStatus();
        if (!config().fishApiKey.isBlank() && status.state() == TtsController.CatalogState.IDLE) refresh(false);
    }

    private void rebuildList() {
        if (voiceList == null) return;
        String query = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        List<FishVoiceModel> voices = config().fishVoiceCatalog.stream()
                .filter(voice -> query.isBlank() || voice.name.toLowerCase(Locale.ROOT).contains(query) || voice.id.toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing((FishVoiceModel voice) -> voice.source == FishVoiceModel.Source.ACCOUNT ? 0 : 1)
                        .thenComparing(voice -> voice.name, String.CASE_INSENSITIVE_ORDER)).toList();
        String selectedId = voiceList.getSelected() == null ? "" : voiceList.getSelected().voice.id;
        voiceList.setVoices(voices);
        voiceList.children().stream().filter(entry -> entry.voice.id.equalsIgnoreCase(selectedId)).findFirst().ifPresent(voiceList::setSelected);
    }

    @Override
    public void tick() {
        super.tick();
        boolean selected = voiceList != null && voiceList.getSelected() != null;
        if (selectButton != null) selectButton.active = selected;
        if (detailsButton != null) detailsButton.active = selected;
        if (refreshButton != null) refreshButton.active = !config().fishApiKey.isBlank()
                && controller().fishCatalogStatus().state() != TtsController.CatalogState.SYNCING;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, 7, 0xFFFFFFFF);
        TtsController.FishCatalogSnapshot status = controller().fishCatalogStatus();
        if (status.state() == TtsController.CatalogState.SYNCING) {
            graphics.drawString(font, Component.translatable("gui.openyourmouth.fish.catalog.syncing"), width / 2 + 154, 28, 0xFFFFD54F, false);
        }
    }

    private void selectVoice() {
        VoiceEntry entry = voiceList.getSelected();
        if (entry == null) return;
        config().fishReferenceId = entry.voice.id;
        save();
        rebuildList();
    }

    private void openDetails() {
        VoiceEntry entry = voiceList.getSelected();
        if (entry != null) minecraft.setScreen(new FishVoiceDetailsScreen(this, entry.voice));
    }

    private void refresh() { refresh(true); }

    private void refresh(boolean force) {
        controller().syncFishCatalog(force).whenComplete((ignored, error) -> minecraft.execute(() -> {
            if (minecraft.screen == this) rebuildWidgets();
        }));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private final class VoiceList extends ObjectSelectionList<VoiceEntry> {
        VoiceList(int width, int height, int y, int entryHeight) {
            super(FishVoiceCatalogScreen.this.minecraft, width, height, y, entryHeight);
        }

        void setVoices(List<FishVoiceModel> voices) {
            replaceEntries(voices.stream().map(VoiceEntry::new).toList());
        }

        @Override public int getRowWidth() { return Math.min(300, FishVoiceCatalogScreen.this.width - 24); }
        @Override protected int scrollBarX() { return FishVoiceCatalogScreen.this.width / 2 + getRowWidth() / 2 + 4; }
    }

    private final class VoiceEntry extends ObjectSelectionList.Entry<VoiceEntry> {
        private final FishVoiceModel voice;

        VoiceEntry(FishVoiceModel voice) {
            this.voice = voice;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            int color = voice.id.equalsIgnoreCase(config().fishReferenceId) ? 0xFF9BE564 : 0xFFFFFFFF;
            String active = voice.id.equalsIgnoreCase(config().fishReferenceId) ? " ✓" : "";
            graphics.drawString(font, Component.literal(shorten(voice.name, 42) + active), getContentX() + 4, getContentY() + 3, color, false);
            Component source = Component.translatable(voice.source == FishVoiceModel.Source.ACCOUNT
                    ? "gui.openyourmouth.fish.catalog.source.account" : "gui.openyourmouth.fish.catalog.source.manual");
            graphics.drawString(font, source, getContentX() + 4, getContentY() + 16, 0xFF909090, false);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            voiceList.setSelected(this);
            if (doubleClick) selectVoice();
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.translatable("gui.openyourmouth.fish.catalog.narration", voice.name,
                    Component.translatable(voice.source == FishVoiceModel.Source.ACCOUNT
                            ? "gui.openyourmouth.fish.catalog.source.account" : "gui.openyourmouth.fish.catalog.source.manual"));
        }
    }

    private static String shorten(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
    }

    private static TtsController controller() { return OpenYourMouthClient.controller(); }
    private static TtsConfig config() { return controller().config(); }
    private static void save() { controller().saveConfig(); }
}
