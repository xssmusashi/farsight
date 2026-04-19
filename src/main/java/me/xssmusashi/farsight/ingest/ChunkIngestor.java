package me.xssmusashi.farsight.ingest;

import me.xssmusashi.farsight.core.lod.LodPyramid;
import me.xssmusashi.farsight.core.mesh.GreedyMesher;
import me.xssmusashi.farsight.core.mesh.QuadCounter;
import me.xssmusashi.farsight.core.storage.LmdbStorage;
import me.xssmusashi.farsight.core.storage.SectionCodec;
import me.xssmusashi.farsight.core.voxel.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * Async pipeline: chunk snapshot → Section → LoD pyramid → greedy mesh
 *   → persist voxel data to LMDB.
 *
 * <p>Pipeline stages run on a dedicated {@link ForkJoinPool} sized to
 * {@code cores - 2} to leave room for the Minecraft main thread and the
 * render thread.</p>
 */
public final class ChunkIngestor implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ChunkIngestor.class);

    private final LmdbStorage storage;
    private final ForkJoinPool pool;
    private final IngestStats stats;
    private final boolean ownsPool;

    public ChunkIngestor(LmdbStorage storage) {
        this(storage, defaultPool(), true);
    }

    public ChunkIngestor(LmdbStorage storage, ForkJoinPool pool, boolean ownsPool) {
        this.storage = storage;
        this.pool = pool;
        this.ownsPool = ownsPool;
        this.stats = new IngestStats();
    }

    private static ForkJoinPool defaultPool() {
        int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        return new ForkJoinPool(parallelism);
    }

    public IngestStats stats() { return stats; }

    /**
     * Submits a snapshot for processing. Returns a future that completes once
     * the snapshot is persisted (and optionally meshed). Errors are recorded
     * in {@link #stats()}.
     */
    public CompletableFuture<Void> submit(ChunkSnapshot snapshot) {
        return CompletableFuture.runAsync(() -> {
            try {
                process(snapshot);
            } catch (RuntimeException e) {
                stats.errors.incrementAndGet();
                LOG.error("ingest failed for {}", snapshot.key(), e);
                throw e;
            }
        }, pool);
    }

    /** Synchronous variant used by tests and the {@code /farsight rebuild} command. */
    public void process(ChunkSnapshot snapshot) {
        Section section = snapshot.toSection();

        byte[] raw = section.serialize();
        byte[] framed = SectionCodec.encode(raw);
        storage.put(snapshot.key(), framed);

        // Build the mip pyramid proactively — useful for Phase 5 GPU consumption.
        // Storage of mip levels is deferred until cross-section aggregation is in place.
        LodPyramid.build(section);

        // Count polygons produced by meshing the native level — useful for stats.
        QuadCounter counter = new QuadCounter();
        int quads = new GreedyMesher().mesh(section, counter);

        stats.sectionsIngested.incrementAndGet();
        stats.bytesWritten.addAndGet(framed.length);
        stats.quadsProduced.addAndGet(quads);
    }

    @Override
    public void close() {
        if (ownsPool) {
            pool.shutdown();
        }
    }
}
