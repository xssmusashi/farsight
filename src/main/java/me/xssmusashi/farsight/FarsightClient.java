package me.xssmusashi.farsight;

import me.xssmusashi.farsight.commands.FarsightCommand;
import me.xssmusashi.farsight.ingest.ChunkIngestor;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

public final class FarsightClient implements ClientModInitializer {
    public static final String MOD_ID = "farsight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Current active ingestor — populated on world join, cleared on disconnect. */
    public static final AtomicReference<ChunkIngestor> ACTIVE_INGESTOR = new AtomicReference<>();

    @Override
    public void onInitializeClient() {
        LOGGER.info("Farsight client initialising — alpha");
        FarsightCommand.register(ACTIVE_INGESTOR::get);
    }
}
