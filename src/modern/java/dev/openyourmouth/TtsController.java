package dev.openyourmouth;

import dev.openyourmouth.audio.TtsPlayback;
import dev.openyourmouth.audio.LocalTtsMonitor;
import dev.openyourmouth.config.ConfigStore;
import dev.openyourmouth.config.FishVoiceModel;
import dev.openyourmouth.config.TtsConfig;
import dev.openyourmouth.tts.EdgeTtsProvider;
import dev.openyourmouth.tts.FishAudioProvider;
import dev.openyourmouth.tts.FishVoiceCatalogService;
import dev.openyourmouth.tts.GoogleTranslateProvider;
import dev.openyourmouth.tts.SpeechTextNormalizer;
import dev.openyourmouth.tts.TtsHttpException;
import dev.openyourmouth.tts.TtsProvider;
import dev.openyourmouth.tts.VoicevoxCatalogService;
import dev.openyourmouth.tts.VoicevoxProvider;
import dev.openyourmouth.tts.VoicevoxStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class TtsController implements AutoCloseable {
    private final ConfigStore store;
    private final TtsConfig config;
    private final TtsPlayback playback = new TtsPlayback();
    private final LocalTtsMonitor localMonitor = new LocalTtsMonitor();
    private final TtsQueue queue = new TtsQueue();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "OpenYourMouth-TTS");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<CacheKey, short[]> cache = new LinkedHashMap<>(64, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<CacheKey, short[]> eldest) { return size() > 64; }
    };
    private final TtsProvider translate = new GoogleTranslateProvider();
    private final TtsProvider edge = new EdgeTtsProvider();
    private final TtsProvider fish = new FishAudioProvider();
    private final TtsProvider voicevox = new VoicevoxProvider();
    private final FishVoiceCatalogService fishCatalog = new FishVoiceCatalogService();
    private final VoicevoxCatalogService voicevoxCatalog = new VoicevoxCatalogService();
    private final AtomicLong skipGeneration = new AtomicLong();
    private final Object fishSyncLock = new Object();
    private volatile boolean closed;
    private volatile boolean processing;
    private volatile Activity activity = Activity.OFF;
    private volatile String lastError = "";
    private volatile CatalogState fishCatalogState = CatalogState.IDLE;
    private volatile String fishCatalogError = "";
    private boolean fishCatalogAttempted;
    private CompletableFuture<Integer> fishCatalogSync;
    private volatile CatalogState voicevoxCatalogState = CatalogState.IDLE;
    private volatile String voicevoxCatalogError = "";
    private volatile List<VoicevoxStyle> voicevoxStyles = List.of();
    private final Object voicevoxSyncLock = new Object();
    private CompletableFuture<List<VoicevoxStyle>> voicevoxCatalogSync;

    public enum Activity { OFF, READY, SYNTHESIZING, PLAYING, ERROR }
    public enum CatalogState { NOT_CONFIGURED, IDLE, SYNCING, READY, ERROR }
    public record StatusSnapshot(Activity activity, int queued, String error) {}
    public record FishCatalogSnapshot(CatalogState state, int accountVoices, String error) {}
    public record VoicevoxCatalogSnapshot(CatalogState state, List<VoicevoxStyle> styles, String error, boolean loopback) {}

    public TtsController(ConfigStore store, TtsConfig config) {
        this.store = store;
        this.config = config;
        worker.submit(this::runQueue);
    }

    public TtsConfig config() { return config; }
    public TtsPlayback playback() { return playback; }
    public void saveConfig() { store.save(); }
    public StatusSnapshot status() {
        Activity visible = activity;
        if (!processing && visible != Activity.ERROR) {
            visible = config.enabled ? Activity.READY : Activity.OFF;
        }
        return new StatusSnapshot(visible, queue.size(), lastError);
    }

    public FishCatalogSnapshot fishCatalogStatus() {
        CatalogState state = config.fishApiKey.isBlank() && TtsConfig.isOfficialFishEndpoint(config.fishEndpoint)
                ? CatalogState.NOT_CONFIGURED : fishCatalogState;
        int count = (int) config.fishVoiceCatalog.stream().filter(voice -> voice.source == FishVoiceModel.Source.ACCOUNT).count();
        return new FishCatalogSnapshot(state, count, fishCatalogError);
    }

    public VoicevoxCatalogSnapshot voicevoxCatalogStatus() {
        return new VoicevoxCatalogSnapshot(voicevoxCatalogState, voicevoxStyles, voicevoxCatalogError,
                VoicevoxProvider.isLoopback(config.voicevoxEndpoint));
    }

    public void onChat(String message) {
        if (isSkipTrigger(message)) {
            skipCurrent();
            return;
        }
        if (!config.enabled || message == null || message.isBlank() || message.startsWith("/")) return;
        enqueueSpeech(message, false);
    }

    public boolean speakOnce(String message) {
        return enqueueSpeech(message, true);
    }

    private boolean enqueueSpeech(String message, boolean oneShot) {
        if (message == null || message.isBlank()) return false;
        if (!config.consentAccepted) {
            if (!oneShot) {
                config.enabled = false;
                store.save();
                OpenYourMouthClient.openConsentScreen();
            } else {
                String text = message;
                OpenYourMouthClient.openConsentScreen(false, () -> speakOnce(text));
            }
            return true;
        }
        if (!OpenYourMouthClient.hasUsableBackend()) {
            notifyPlayer("message.openyourmouth.backend_unavailable");
            return false;
        }
        if (config.provider == TtsConfig.Provider.FISH_AUDIO && FishAudioProvider.needsApiKey(config)) {
            notifyPlayer("message.openyourmouth.fish_api_key_missing");
            return false;
        }
        if (!queue.offer(limitCodePoints(message, 4_500))) {
            notifyPlayer("message.openyourmouth.queue_full");
            return false;
        }
        return true;
    }

    private void runQueue() {
        while (!closed) {
            try {
                String text = queue.take();
                processing = true;
                activity = Activity.SYNTHESIZING;
                lastError = "";
                long generation = skipGeneration.get();
                TtsConfig snapshot = copyConfig();
                String synthesisText = SpeechTextNormalizer.forSynthesis(
                        text, snapshot.ignoreAccents, snapshot.ignoreUppercase);
                short[] audio = synthesizeCached(synthesisText, snapshot);
                if (generation != skipGeneration.get()) continue;
                // The capture thread can take a while to restart after changing worlds/servers.
                // Keep a generous watchdog while still allowing disconnect/close to cancel immediately.
                long audioSeconds = Math.max(1L, (audio.length + 47_999L) / 48_000L);
                long timeoutSeconds = Math.max(120L, audioSeconds * 3L + 30L);
                activity = Activity.PLAYING;
                var completion = playback.play(audio);
                if (snapshot.hearSelf) localMonitor.play(audio, snapshot.volume);
                if (generation != skipGeneration.get()) {
                    playback.stop();
                    continue;
                }
                completion.orTimeout(timeoutSeconds, TimeUnit.SECONDS).join();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            } catch (CompletionException completion) {
                playback.stop();
                if (!closed) OpenYourMouthClient.LOGGER.debug("TTS playback interrupted", completion.getCause());
            } catch (Exception exception) {
                OpenYourMouthClient.LOGGER.warn("TTS synthesis failed: {}", exception.toString());
                activity = Activity.ERROR;
                lastError = safeError(exception);
                notifyPlayer("message.openyourmouth.synthesis_failed", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            } finally {
                processing = false;
                if (activity != Activity.ERROR) activity = config.enabled ? Activity.READY : Activity.OFF;
            }
        }
    }

    /** Synchronizes Fish account voices once per session unless explicitly forced. */
    public CompletableFuture<Integer> syncFishCatalog(boolean force) {
        synchronized (fishSyncLock) {
            if (config.fishApiKey.isBlank()) {
                fishCatalogState = CatalogState.NOT_CONFIGURED;
                fishCatalogError = "API_KEY_MISSING";
                return CompletableFuture.failedFuture(new IllegalStateException("Fish Audio API key is not configured"));
            }
            if (fishCatalogSync != null && !fishCatalogSync.isDone()) return fishCatalogSync;
            if (!force && fishCatalogAttempted) return CompletableFuture.completedFuture(accountVoiceCount());
            fishCatalogAttempted = true;
            fishCatalogState = CatalogState.SYNCING;
            fishCatalogError = "";
            fishCatalogSync = fishCatalog.fetchOwned(copyConfig()).thenApply(voices -> {
                mergeFishAccountVoices(voices);
                store.save();
                fishCatalogState = CatalogState.READY;
                fishCatalogError = "";
                return voices.size();
            }).whenComplete((ignored, error) -> {
                if (error == null) return;
                fishCatalogState = CatalogState.ERROR;
                fishCatalogError = classifyCatalogError(unwrap(error));
            });
            return fishCatalogSync;
        }
    }

    public void resetFishCatalogSession() {
        synchronized (fishSyncLock) {
            fishCatalogAttempted = false;
            fishCatalogState = config.fishApiKey.isBlank() ? CatalogState.NOT_CONFIGURED : CatalogState.IDLE;
            fishCatalogError = "";
        }
    }

    public CompletableFuture<List<VoicevoxStyle>> refreshVoicevoxCatalog() {
        synchronized (voicevoxSyncLock) {
            if (voicevoxCatalogSync != null && !voicevoxCatalogSync.isDone()) return voicevoxCatalogSync;
            voicevoxCatalogState = CatalogState.SYNCING;
            voicevoxCatalogError = "";
            voicevoxCatalogSync = voicevoxCatalog.fetch(config.voicevoxEndpoint).thenApply(styles -> {
                voicevoxStyles = styles;
                voicevoxCatalogState = CatalogState.READY;
                if (!styles.isEmpty()) {
                    VoicevoxStyle selected = styles.stream().filter(style -> style.id() == config.voicevoxStyleId).findFirst().orElse(styles.getFirst());
                    config.voicevoxStyleId = selected.id();
                    config.voicevoxStyleName = selected.name();
                    store.save();
                }
                return styles;
            }).whenComplete((ignored, error) -> {
                if (error == null) return;
                voicevoxCatalogState = CatalogState.ERROR;
                voicevoxCatalogError = safeCatalogError(unwrap(error));
            });
            return voicevoxCatalogSync;
        }
    }

    public void setProvider(TtsConfig.Provider provider) {
        if (provider == null || provider == config.provider) return;
        config.provider = provider;
        if (provider == TtsConfig.Provider.EDGE_TTS) {
            config.edgeVoice = dev.openyourmouth.tts.EdgeVoiceCatalog.selectedOrDefault(config.locale, config.edgeVoice);
            dev.openyourmouth.tts.EdgeVoiceCatalog.refreshAsync();
        } else if (provider == TtsConfig.Provider.VOICEVOX) {
            refreshVoicevoxCatalog();
        }
        synchronized (cache) { cache.clear(); }
        store.save();
    }

    public boolean isSkipTrigger(String message) {
        return config.enabled && message != null && message.strip().equalsIgnoreCase(config.skipTrigger);
    }

    public boolean skipCurrent() {
        if (!processing && !playback.isPlaying()) {
            notifyPlayer("message.openyourmouth.nothing_to_skip");
            return false;
        }
        skipGeneration.incrementAndGet();
        playback.stop();
        localMonitor.stop();
        notifyPlayerYellow("message.openyourmouth.skipped");
        return true;
    }

    private TtsProvider provider(TtsConfig value) {
        return switch (value.provider) {
            case GOOGLE_TRANSLATE -> translate;
            case EDGE_TTS -> edge;
            case FISH_AUDIO -> fish;
            case VOICEVOX -> voicevox;
        };
    }

    private short[] synthesizeCached(String text, TtsConfig snapshot) throws Exception {
        CacheKey key = new CacheKey(text, snapshot.provider, snapshot.locale, snapshot.edgeVoice, snapshot.speakingRate, snapshot.pitch,
                snapshot.fishEndpoint, snapshot.fishModel, snapshot.fishReferenceId, snapshot.fishLatency,
                snapshot.fishSpeed, snapshot.fishTemperature, snapshot.fishTopP, snapshot.fishNormalize,
                snapshot.voicevoxEndpoint, snapshot.voicevoxStyleId, snapshot.voicevoxSpeed, snapshot.voicevoxPitch,
                snapshot.voicevoxIntonation,
                snapshot.ignoreAccents, snapshot.ignoreUppercase);
        short[] audio;
        synchronized (cache) { audio = cache.get(key); }
        if (audio == null) {
            audio = provider(snapshot).synthesize(text, snapshot);
            synchronized (cache) { cache.put(key, audio); }
        }
        return audio;
    }

    private TtsConfig copyConfig() {
        TtsConfig value = new TtsConfig();
        value.consentAccepted = config.consentAccepted;
        value.enabled = config.enabled;
        value.hearSelf = config.hearSelf;
        value.ignoreAccents = config.ignoreAccents;
        value.ignoreUppercase = config.ignoreUppercase;
        value.backend = config.backend;
        value.provider = config.provider;
        value.locale = config.locale;
        value.edgeVoice = config.edgeVoice;
        value.fishEndpoint = config.fishEndpoint;
        value.fishApiKey = config.fishApiKey;
        value.fishModel = config.fishModel;
        value.fishReferenceId = config.fishReferenceId;
        value.fishLatency = config.fishLatency;
        value.fishSpeed = config.fishSpeed;
        value.fishTemperature = config.fishTemperature;
        value.fishTopP = config.fishTopP;
        value.fishNormalize = config.fishNormalize;
        value.voicevoxEndpoint = config.voicevoxEndpoint;
        value.voicevoxStyleId = config.voicevoxStyleId;
        value.voicevoxStyleName = config.voicevoxStyleName;
        value.voicevoxSpeed = config.voicevoxSpeed;
        value.voicevoxPitch = config.voicevoxPitch;
        value.voicevoxIntonation = config.voicevoxIntonation;
        value.speakingRate = config.speakingRate;
        value.pitch = config.pitch;
        value.volume = config.volume;
        value.skipTrigger = config.skipTrigger;
        value.sendChatMessages = config.sendChatMessages;
        return value;
    }

    public void clear() {
        queue.clear();
        playback.stop();
        localMonitor.stop();
        lastError = "";
        activity = config.enabled ? Activity.READY : Activity.OFF;
    }

    public void setEnabled(boolean enabled) {
        if (enabled && !config.consentAccepted) {
            OpenYourMouthClient.openConsentScreen();
            return;
        }
        config.enabled = enabled;
        activity = enabled ? Activity.READY : Activity.OFF;
        lastError = "";
        store.save();
        notifyPlayer(enabled ? "message.openyourmouth.enabled" : "message.openyourmouth.disabled");
    }

    public void toggleEnabled() { setEnabled(!config.enabled); }

    public void setHearSelf(boolean enabled) {
        config.hearSelf = enabled;
        if (!enabled) localMonitor.stop();
        store.save();
        notifyPlayer(enabled ? "message.openyourmouth.hear_self_on" : "message.openyourmouth.hear_self_off");
    }

    public void toggleHearSelf() { setHearSelf(!config.hearSelf); }

    public void setIgnoreAccents(boolean enabled) {
        config.ignoreAccents = enabled;
        store.save();
        synchronized (cache) { cache.clear(); }
    }

    public void setIgnoreUppercase(boolean enabled) {
        config.ignoreUppercase = enabled;
        store.save();
        synchronized (cache) { cache.clear(); }
    }

    public boolean shouldSuppressChat(String message) {
        return config.enabled && !config.sendChatMessages && message != null && !message.isBlank() && !message.startsWith("/") && !isSkipTrigger(message);
    }

    public void setSendChatMessages(boolean enabled) {
        config.sendChatMessages = enabled;
        store.save();
        notifyPlayer(enabled ? "message.openyourmouth.chat_on" : "message.openyourmouth.chat_off");
    }

    public void toggleSendChatMessages() { setSendChatMessages(!config.sendChatMessages); }

    public void showCommandHelp() {
        notifyPlayer("message.openyourmouth.command_help");
        notifyPlayer("message.openyourmouth.status", Component.translatable(config.enabled ? "options.on" : "options.off"), Component.translatable(config.hearSelf ? "options.on" : "options.off"), Component.translatable(config.sendChatMessages ? "options.on" : "options.off"));
    }

    @Override public void close() {
        closed = true;
        clear();
        localMonitor.close();
        worker.shutdownNow();
        synchronized (cache) { cache.clear(); }
    }

    private static String limitCodePoints(String value, int maximum) {
        if (value.codePointCount(0, value.length()) <= maximum) return value;
        return value.substring(0, value.offsetByCodePoints(0, maximum));
    }

    private int accountVoiceCount() {
        return (int) config.fishVoiceCatalog.stream().filter(voice -> voice.source == FishVoiceModel.Source.ACCOUNT).count();
    }

    private void mergeFishAccountVoices(List<FishVoiceModel> remote) {
        Map<String, FishVoiceModel> merged = new LinkedHashMap<>();
        for (FishVoiceModel voice : config.fishVoiceCatalog) {
            if (voice.source == FishVoiceModel.Source.MANUAL) merged.put(voice.id.toLowerCase(Locale.ROOT), voice);
        }
        for (FishVoiceModel voice : remote) {
            String key = voice.id.toLowerCase(Locale.ROOT);
            merged.putIfAbsent(key, new FishVoiceModel(voice.name, voice.id, FishVoiceModel.Source.ACCOUNT));
        }
        List<FishVoiceModel> sorted = new ArrayList<>(merged.values());
        sorted.sort(Comparator.comparing((FishVoiceModel voice) -> voice.source).thenComparing(voice -> voice.name, String.CASE_INSENSITIVE_ORDER));
        config.fishVoiceCatalog = sorted;
        if (config.fishReferenceId.isBlank() && !remote.isEmpty()) config.fishReferenceId = remote.getFirst().id;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) current = current.getCause();
        return current;
    }

    private String safeError(Throwable error) {
        String value = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (!config.fishApiKey.isBlank()) value = value.replace(config.fishApiKey.strip(), "***");
        value = value.replace('\r', ' ').replace('\n', ' ').strip();
        return value.length() > 160 ? value.substring(0, 160) : value;
    }

    private static String classifyCatalogError(Throwable error) {
        if (error instanceof dev.openyourmouth.tts.TtsHttpException http) {
            return switch (http.status()) {
                case 401, 403 -> "AUTH";
                case 402 -> "CREDIT";
                case 429 -> "RATE_LIMIT";
                default -> http.status() >= 500 ? "UNAVAILABLE" : "INVALID_RESPONSE";
            };
        }
        if (error instanceof java.net.http.HttpTimeoutException || error instanceof java.net.ConnectException) return "OFFLINE";
        return "UNAVAILABLE";
    }

    private static String safeCatalogError(Throwable error) {
        if (error instanceof java.net.http.HttpTimeoutException || error instanceof java.net.ConnectException) return "OFFLINE";
        if (error instanceof TtsHttpException http) return "HTTP_" + http.status();
        return "UNAVAILABLE";
    }

    private static void notifyPlayer(String key, Object... arguments) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.translatable(key, arguments));
        });
    }

    private static void notifyPlayerYellow(String key) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.YELLOW));
            }
        });
    }

    private record CacheKey(String text, TtsConfig.Provider provider, String locale, String voice, double rate, double pitch,
                            String fishEndpoint, String fishModel, String fishReferenceId, String fishLatency,
                            double fishSpeed, double fishTemperature, double fishTopP, boolean fishNormalize,
                            String voicevoxEndpoint, int voicevoxStyleId, double voicevoxSpeed, double voicevoxPitch,
                            double voicevoxIntonation,
                            boolean ignoreAccents, boolean ignoreUppercase) {
        private CacheKey { Objects.requireNonNull(text); }
    }
}
