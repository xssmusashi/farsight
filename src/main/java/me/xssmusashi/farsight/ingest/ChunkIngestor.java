package me.xssmusashi.farsight.ingest;

import me.xssmusashi.farsight.core.lod.LodPyramid;
import me.xssmusashi.farsight.core.mesh.GreedyMesher;
import me.xssmusashi.farsight.core.mesh.MeshBuilder;
import me.xssmusashi.farsight.core.storage.MeshBlob;
import me.xssmusashi.farsight.core.storage.SectionCodec;
import me.xssmusashi.farsight.core.storage.SectionKey;
import me.xssmusashi.farsight.core.storage.SectionStore;
import me.xssmusashi.farsight.core.voxel.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** Keep the in-flight task count low — each queued task holds a {@link Section}
     *  plus mesh buffers (~100-200 KB) and world join fires hundreds of chunks
     *  at once. 64 × ~150 KB ≈ 10 MB peak, well under any JVM heap budget. */
    public static final int MAX_PENDING = 64;

    private final SectionStore storage;
    private final ForkJoinPool pool;
    private final IngestStats stats;
    private final boolean ownsPool;
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicInteger dropped = new AtomicInteger();

    public ChunkIngestor(SectionStore storage) {
        this(storage, defaultPool(), true);
    }

    public ChunkIngestor(SectionStore storage, ForkJoinPool pool, boolean ownsPool) {
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
    public int pendingCount() { return pending.get(); }
    public int droppedCount() { return dropped.get(); }

    /**
     * Submits a snapshot for processing. If the pool already has
     * {@link #MAX_PENDING} tasks in flight, drops the snapshot and
     * increments {@link #droppedCount()} — world-join bursts must not
     * push the JVM into OOM.
     */
    public CompletableFuture<Void> submit(ChunkSnapshot snapshot) {
        if (pending.get() >= MAX_PENDING) {
            int d = dropped.incrementAndGet();
            if ((d & 0xFF) == 0) {   // log every 256 drops
                LOG.info("ingest backpressure: dropped {} sections total (pending={}/{})",
                    d, pending.get(), MAX_PENDING);
            }
            return CompletableFuture.completedFuture(null);
        }
        pending.incrementAndGet();
        return CompletableFuture.runAsync(() -> {
            try {
                process(snapshot);
            } catch (RuntimeException e) {
                stats.errors.incrementAndGet();
                LOG.error("ingest failed for {}", snapshot.key(), e);
                throw e;
            } finally {
                pending.decrementAndGet();
            }
        }, pool);
    }

    /** Synchronous variant used by tests and the {@code /farsight rebuild} command. */
    public void process(ChunkSnapshot snapshot) {
        Section section = snapshot.toSection();

        byte[] raw = section.serialize();
        byte[] framed = SectionCodec.encode(raw);
        storage.put(snapshot.key(), framed);

        // Build the mip pyramid — intra-section mips will feed cross-section
        // aggregation in a later pass.
        LodPyramid.build(section);

        // Bake the native-level mesh and persist it alongside the voxel data.
        long[] voxels = new long[Section.VOLUME];
        for (int i = 0; i < Section.VOLUME; i++) voxels[i] = section.getByIndex(i);

        MeshBuilder mb = new MeshBuilder(16 * 1024).sectionContext(voxels);
        int quads = new GreedyMesher().mesh(section, mb);

        SectionKey key = snapshot.key();
        if (quads > 0) {
            int scale = 16;      // chunk-aligned ingest — one MC 16³ section per Farsight 32³
            int originX = key.sx() * scale;
            int originY = key.sy() * scale;
            int originZ = key.sz() * scale;
            MeshBlob blob = new MeshBlob(originX, originY, originZ, 1.0f, quads, mb.toByteArray());
            byte[] meshBytes = blob.encode();
            storage.putMesh(key, meshBytes);
            stats.bytesWritten.addAndGet(meshBytes.length);
            PendingSections.publish(key);
        }

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
