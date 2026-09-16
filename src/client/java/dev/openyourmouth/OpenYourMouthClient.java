package dev.openyourmouth;

import dev.openyourmouth.config.ConfigStore;
import dev.openyourmouth.config.TtsConfig;
import dev.openyourmouth.gui.ConsentScreen;
import dev.openyourmouth.integration.plasmo.OpenYourMouthPlasmoAddon;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.plo.voice.api.client.PlasmoVoiceClient;
import com.mojang.brigadier.arguments.StringArgumentType;

public final class OpenYourMouthClient implements ClientModInitializer {
    public static final String MOD_ID = "openyourmouth";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final ConfigStore CONFIG_STORE = new ConfigStore();
    private static TtsController controller;
    private static boolean simpleLoaded;
    private static boolean plasmoLoaded;

    @Override
    public void onInitializeClient() {
        simpleLoaded = FabricLoader.getInstance().isModLoaded("voicechat");
        plasmoLoaded = FabricLoader.getInstance().isModLoaded("plasmovoice");
        controller = new TtsController(CONFIG_STORE, CONFIG_STORE.load());
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (controller.isSkipTrigger(message)) {
                controller.skipCurrent();
                return false;
            }
            if (controller.shouldSuppressChat(message)) {
                controller.onChat(message);
                return false;
            }
            return true;
        });
        ClientSendMessageEvents.CHAT.register(controller::onChat);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("tts")
                        .executes(context -> { controller.showCommandHelp(); return 1; })
                        .then(ClientCommandManager.literal("skip").executes(context -> controller.skipCurrent() ? 1 : 0))
                        .then(ClientCommandManager.literal("say")
                                .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                                        .executes(context -> controller.speakOnce(StringArgumentType.getString(context, "text")) ? 1 : 0)))
                        .then(ClientCommandManager.literal("on").executes(context -> { controller.setEnabled(true); return 1; }))
                        .then(ClientCommandManager.literal("off").executes(context -> { controller.setEnabled(false); return 1; }))
                        .then(ClientCommandManager.literal("toggle").executes(context -> { controller.toggleEnabled(); return 1; }))
                        .then(ClientCommandManager.literal("hearself")
                                .executes(context -> { controller.toggleHearSelf(); return 1; })
                                .then(ClientCommandManager.literal("on").executes(context -> { controller.setHearSelf(true); return 1; }))
                                .then(ClientCommandManager.literal("off").executes(context -> { controller.setHearSelf(false); return 1; })))
                        .then(ClientCommandManager.literal("chat")
                                .executes(context -> { controller.toggleSendChatMessages(); return 1; })
                                .then(ClientCommandManager.literal("on").executes(context -> { controller.setSendChatMessages(true); return 1; }))
                                .then(ClientCommandManager.literal("off").executes(context -> { controller.setSendChatMessages(false); return 1; })))
                        .then(ClientCommandManager.literal("status").executes(context -> { controller.showCommandHelp(); return 1; }))
                        .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                                .executes(context -> controller.speakOnce(StringArgumentType.getString(context, "text")) ? 1 : 0))
        ));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> controller.clear());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> controller.close());
        if (plasmoLoaded) PlasmoVoiceClient.getAddonsLoader().load(new OpenYourMouthPlasmoAddon());
        LOGGER.info("OpenYourMouth initialized (Simple Voice Chat: {}, Plasmo Voice: {})", simpleLoaded, plasmoLoaded);
    }

    public static TtsController controller() {
        if (controller == null) throw new IllegalStateException("OpenYourMouth is not initialized");
        return controller;
    }

    public static boolean hasUsableBackend() {
        TtsConfig.Backend backend = controller().config().backend;
        if (backend == TtsConfig.Backend.SIMPLE_VOICE_CHAT) return simpleLoaded;
        if (backend == TtsConfig.Backend.PLASMO_VOICE) return plasmoLoaded;
        return simpleLoaded ^ plasmoLoaded;
    }

    public static boolean usesSimple() {
        TtsConfig.Backend backend = controller().config().backend;
        return simpleLoaded && (backend == TtsConfig.Backend.SIMPLE_VOICE_CHAT || (backend == TtsConfig.Backend.AUTO && !plasmoLoaded));
    }

    public static boolean usesPlasmo() {
        TtsConfig.Backend backend = controller().config().backend;
        return plasmoLoaded && (backend == TtsConfig.Backend.PLASMO_VOICE || (backend == TtsConfig.Backend.AUTO && !simpleLoaded));
    }

    public static boolean isSimpleLoaded() { return simpleLoaded; }
    public static boolean isPlasmoLoaded() { return plasmoLoaded; }
    public static boolean areBothVoiceChatsLoaded() { return simpleLoaded && plasmoLoaded; }

    public static void openConsentScreen() {
        openConsentScreen(() -> {});
    }

    public static void openConsentScreen(Runnable onAccepted) {
        openConsentScreen(true, onAccepted);
    }

    public static void openConsentScreen(boolean enableAfterConsent, Runnable onAccepted) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new ConsentScreen(minecraft.screen, onAccepted, enableAfterConsent)));
    }
}
