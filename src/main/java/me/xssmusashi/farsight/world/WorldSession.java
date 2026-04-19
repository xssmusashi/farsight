package me.xssmusashi.farsight.world;

import me.xssmusashi.farsight.FarsightClient;
import me.xssmusashi.farsight.core.storage.FilesystemStorage;
import me.xssmusashi.farsight.core.storage.LmdbStorage;
import me.xssmusashi.farsight.core.storage.SectionStore;
import me.xssmusashi.farsight.ingest.ChunkIngestor;

import java.nio.file.Path;

/**
 * Per-world runtime state — a {@link SectionStore} rooted under the mod's
 * cache directory and the {@link ChunkIngestor} that feeds it.
 *
 * <p>Tries to open LMDB first; on any failure (typically the JDK 17+ module
 * lockdown refusing reflection into {@code java.nio.Buffer.address} when
 * {@code --add-opens} wasn't granted and {@link me.xssmusashi.farsight.boot.ModuleUnlocker}
 * couldn't patch it in at runtime), falls back to {@link FilesystemStorage}
 * transparently — Farsight keeps working, just with slower per-section
 * I/O.</p>
 */
public final class WorldSession implements AutoCloseable {
    private final String worldId;
    private final Path cacheRoot;
    private final SectionStore storage;
    private final String backend;
    private final ChunkIngestor ingestor;

    public WorldSession(String worldId, Path cacheRoot) {
        this.worldId = worldId;
        this.cacheRoot = cacheRoot;
        SectionStore s;
        String backendName;
        try {
            s = new LmdbStorage(cacheRoot);
            backendName = "lmdb";
        } catch (Throwable t) {
            FarsightClient.LOGGER.warn(
                "LMDB unavailable for '{}' ({}), falling back to filesystem storage", worldId, t.toString());
            s = new FilesystemStorage(cacheRoot);
            backendName = "filesystem";
        }
        this.storage = s;
        this.backend = backendName;
        this.ingestor = new ChunkIngestor(storage);
        FarsightClient.LOGGER.info("WorldSession '{}' ready on {} backend at {}", worldId, backend, cacheRoot);
    }

    public String worldId()         { return worldId; }
    public Path   cacheRoot()       { return cacheRoot; }
    public SectionStore storage()   { return storage; }
    public String backend()         { return backend; }
    public ChunkIngestor ingestor() { return ingestor; }

    @Override
    public void close() {
        try { ingestor.close(); } catch (Exception ignored) {}
        try { storage.close();  } catch (Exception ignored) {}
    }
}
