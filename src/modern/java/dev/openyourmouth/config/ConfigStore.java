package dev.openyourmouth.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.openyourmouth.OpenYourMouthClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory = FabricLoader.getInstance().getConfigDir().resolve(OpenYourMouthClient.MOD_ID);
    private final Path path = directory.resolve("config.json");
    private TtsConfig config;

    public synchronized TtsConfig load() {
        try {
            Files.createDirectories(directory);
            if (Files.isRegularFile(path)) {
                String json = Files.readString(path);
                // Preserve all unrelated settings when upgrading from the removed paid provider.
                json = json.replace("\"provider\": \"GOOGLE_CLOUD\"", "\"provider\": \"EDGE_TTS\"");
                config = GSON.fromJson(json, TtsConfig.class);
            }
        } catch (Exception exception) {
            OpenYourMouthClient.LOGGER.error("Could not read TTS configuration", exception);
        }
        if (config == null) config = new TtsConfig();
        config.normalize();
        save();
        return config;
    }

    public synchronized void save() {
        if (config == null) return;
        config.normalize();
        try {
            Files.createDirectories(directory);
            Path temporary = directory.resolve("config.json.tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(config, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            OpenYourMouthClient.LOGGER.error("Could not save TTS configuration", exception);
        }
    }

}
