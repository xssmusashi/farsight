package me.xssmusashi.farsight;

import me.xssmusashi.farsight.boot.ModuleUnlocker;
import me.xssmusashi.farsight.commands.FarsightCommand;
import me.xssmusashi.farsight.config.FarsightConfig;
import me.xssmusashi.farsight.ingest.ChunkIngestor;
import me.xssmusashi.farsight.ingest.ChunkObserver;
import me.xssmusashi.farsight.world.WorldLifecycle;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

public final class FarsightClient implements ClientModInitializer {
    public static final String MOD_ID = "xssm_farsight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Current active ingestor — populated on world join, cleared on disconnect. */
    public static final AtomicReference<ChunkIngestor> ACTIVE_INGESTOR = new AtomicReference<>();

    public static FarsightConfig CONFIG = new FarsightConfig();

    @Override
    public void onInitializeClient() {
        // Must run BEFORE any lmdbjava class is referenced — touching
        // org.lmdbjava.ByteBufferProxy triggers its <clinit> which uses
        // reflection into java.nio.Buffer.address.
        boolean unlocked = ModuleUnlocker.unlockJavaNio();
        CONFIG = FarsightConfig.loadOrCreateDefault();
        LOGGER.info("Farsight {} initialising — lodRenderDistance={}, compute culling={}, moduleUnlocker={}",
            MOD_ID, CONFIG.lodRenderDistance, CONFIG.useComputeCulling,
            unlocked ? "ok" : "FAILED (lmdbjava will need --add-opens)");
        FarsightCommand.register(ACTIVE_INGESTOR::get);
        WorldLifecycle.register();
        ChunkObserver.register();
        // Actual draw is triggered from MinecraftMixin at RETURN of renderFrame.
    }
}
