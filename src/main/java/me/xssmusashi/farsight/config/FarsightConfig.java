package me.xssmusashi.farsight.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import me.xssmusashi.farsight.FarsightClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * On-disk JSON config at {@code config/farsight.json} (inside the Minecraft
 * instance). Loaded once during {@code ClientModInitializer.onInitializeClient}
 * and cached. Hot-reload is out of scope.
 */
public final class FarsightConfig {

    public boolean enabled = true;
    public int lodRenderDistance = 32;      // in LoD sections (each 32 native voxels)
    public int maxMemoryMb = 2048;
    public int ingestThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    public boolean useComputeCulling = true;
    public boolean shaderCompat = false;    // Phase 7 feature flag

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create();

    public static Path defaultPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("farsight.json");
    }

    public static FarsightConfig loadOrCreateDefault() {
        return loadOrCreate(defaultPath());
    }

    public static FarsightConfig loadOrCreate(Path path) {
        try {
            if (!Files.exists(path)) {
                FarsightConfig cfg = new FarsightConfig();
                cfg.save(path);
                FarsightClient.LOGGER.info("wrote default config to {}", path);
                return cfg;
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            FarsightConfig cfg = GSON.fromJson(json, FarsightConfig.class);
            if (cfg == null) cfg = new FarsightConfig();
            return cfg;
        } catch (IOException | JsonSyntaxException e) {
            FarsightClient.LOGGER.error("failed to load config, using defaults", e);
            return new FarsightConfig();
        }
    }

    public void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            FarsightClient.LOGGER.error("failed to save config", e);
        }
    }
}
