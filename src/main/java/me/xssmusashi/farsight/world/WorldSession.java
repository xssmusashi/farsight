package me.xssmusashi.farsight.world;

import me.xssmusashi.farsight.core.storage.LmdbStorage;
import me.xssmusashi.farsight.ingest.ChunkIngestor;

import java.nio.file.Path;

/**
 * Per-world runtime state — an {@link LmdbStorage} rooted under the mod's
 * cache directory and the {@link ChunkIngestor} that feeds it.
 *
 * <p>Created on client join, closed on disconnect. One session active at a
 * time; multiplayer servers and single-player worlds both get their own
 * cache directory identified by {@link #worldId()}.</p>
 */
public final class WorldSession implements AutoCloseable {
    private final String worldId;
    private final Path cacheRoot;
    private final LmdbStorage storage;
    private final ChunkIngestor ingestor;

    public WorldSession(String worldId, Path cacheRoot) {
        this.worldId = worldId;
        this.cacheRoot = cacheRoot;
        this.storage = new LmdbStorage(cacheRoot);
        this.ingestor = new ChunkIngestor(storage);
    }

    public String worldId()         { return worldId; }
    public Path   cacheRoot()       { return cacheRoot; }
    public LmdbStorage storage()    { return storage; }
    public ChunkIngestor ingestor() { return ingestor; }

    @Override
    public void close() {
        try { ingestor.close(); } catch (Exception ignored) {}
        try { storage.close();  } catch (Exception ignored) {}
    }
}
