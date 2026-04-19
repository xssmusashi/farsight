package me.xssmusashi.farsight.render;

import me.xssmusashi.farsight.FarsightClient;
import me.xssmusashi.farsight.core.storage.LmdbStorage;
import me.xssmusashi.farsight.core.storage.MeshBlob;
import me.xssmusashi.farsight.core.storage.SectionKey;
import me.xssmusashi.farsight.core.voxel.Section;
import me.xssmusashi.farsight.ingest.PendingSections;
import me.xssmusashi.farsight.world.WorldLifecycle;
import me.xssmusashi.farsight.world.WorldSession;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Drains newly-persisted section keys on the render thread, reads their
 * mesh blobs from LMDB, allocates space in the mega VBO, and registers the
 * section with {@link SectionRegistry}. Rate-limited to a small number of
 * sections per frame so one large import batch does not spike the render
 * thread.
 */
public final class SectionLoader {
    public static final int DEFAULT_MAX_LOADS_PER_FRAME = 8;

    private final SectionVboPool vbo;
    private final SectionRegistry registry;
    private final int maxLoadsPerFrame;

    public SectionLoader(SectionVboPool vbo, SectionRegistry registry) {
        this(vbo, registry, DEFAULT_MAX_LOADS_PER_FRAME);
    }

    public SectionLoader(SectionVboPool vbo, SectionRegistry registry, int maxLoadsPerFrame) {
        this.vbo = vbo;
        this.registry = registry;
        this.maxLoadsPerFrame = maxLoadsPerFrame;
    }

    /** Render-thread tick. Safe to call before a world session exists — no-ops. */
    public void tick() {
        WorldSession session = WorldLifecycle.currentSession();
        if (session == null) return;
        LmdbStorage storage = session.storage();
        for (int i = 0; i < maxLoadsPerFrame; i++) {
            SectionKey key = PendingSections.poll();
            if (key == null) return;
            try {
                loadOne(storage, key);
            } catch (Throwable t) {
                FarsightClient.LOGGER.warn("section load failed for {}", key, t);
            }
        }
    }

    private void loadOne(LmdbStorage storage, SectionKey key) {
        byte[] raw = storage.getMesh(key);
        if (raw == null) return;
        MeshBlob blob = MeshBlob.decode(raw);
        if (blob.quadCount() == 0 || blob.vertexBytes().length == 0) return;

        ByteBuffer bytes = ByteBuffer.wrap(blob.vertexBytes()).order(ByteOrder.LITTLE_ENDIAN);
        long baseVertex = vbo.allocate(bytes);
        if (baseVertex < 0L) {
            FarsightClient.LOGGER.debug("VBO pool full — dropping section {}", key);
            return;
        }
        if (baseVertex > Integer.MAX_VALUE) {
            FarsightClient.LOGGER.warn("baseVertex overflow for {} → {}", key, baseVertex);
            return;
        }
        float extent = Section.SIZE * blob.voxelScale();
        int slot = registry.register(
            blob.originX(), blob.originY(), blob.originZ(),
            extent, blob.voxelScale(),
            (int) baseVertex);
        if (slot < 0) {
            FarsightClient.LOGGER.debug("SectionRegistry full — dropping section {}", key);
        }
    }
}
