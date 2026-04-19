package me.xssmusashi.farsight.world;

import me.xssmusashi.farsight.FarsightClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Binds {@link ClientPlayConnectionEvents#JOIN} / {@link ClientPlayConnectionEvents#DISCONNECT}
 * to {@link WorldSession} construction / destruction. Exposes the active
 * session through {@link FarsightClient#ACTIVE_INGESTOR} so the rest of the
 * mod has a single read-path.
 */
public final class WorldLifecycle {
    private static final AtomicReference<WorldSession> ACTIVE = new AtomicReference<>();

    private WorldLifecycle() {}

    public static WorldSession currentSession() { return ACTIVE.get(); }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(client, handler));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
    }

    private static void onJoin(Minecraft client, net.minecraft.client.multiplayer.ClientPacketListener handler) {
        try {
            String worldId = WorldIdentifier.resolveWorldId(client, handler);
            Path cacheRoot = WorldIdentifier.cacheDirFor(worldId);
            WorldSession session = new WorldSession(worldId, cacheRoot);
            WorldSession previous = ACTIVE.getAndSet(session);
            if (previous != null) previous.close();
            FarsightClient.ACTIVE_INGESTOR.set(session.ingestor());
            FarsightClient.LOGGER.info("Farsight session opened for '{}' at {}", worldId, cacheRoot);
        } catch (Throwable t) {
            FarsightClient.LOGGER.error("failed to open Farsight session", t);
        }
    }

    private static void onDisconnect() {
        WorldSession session = ACTIVE.getAndSet(null);
        FarsightClient.ACTIVE_INGESTOR.set(null);
        if (session != null) {
            try {
                session.close();
                FarsightClient.LOGGER.info("Farsight session closed for '{}'", session.worldId());
            } catch (Throwable t) {
                FarsightClient.LOGGER.error("error closing Farsight session", t);
            }
        }
    }
}
