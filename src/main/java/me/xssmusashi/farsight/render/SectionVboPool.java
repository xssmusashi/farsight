package me.xssmusashi.farsight.render;

import me.xssmusashi.farsight.core.mesh.MeshFormat;
import org.lwjgl.opengl.GL46;

import java.nio.ByteBuffer;

/**
 * Ring allocator into a single mega VBO. Each allocation returns a
 * {@code (baseVertex, vertexCount)} pair that the indirect-draw shader uses
 * directly — no per-section bind, no per-section draw call.
 *
 * <p>Not thread-safe on purpose; must be called from the render thread only.</p>
 */
public final class SectionVboPool implements AutoCloseable {
    private final PersistentMappedBuffer vertexBuffer;
    private final long capacityBytes;
    private long cursor;

    public SectionVboPool(long capacityBytes) {
        this.vertexBuffer = new PersistentMappedBuffer(GL46.GL_ARRAY_BUFFER, capacityBytes);
        this.capacityBytes = capacityBytes;
        this.cursor = 0L;
    }

    /**
     * Copies the supplied vertex bytes into the pool. Returns the baseVertex
     * (in whole-vertex units) or -1 if the pool is full — in that case the
     * caller should evict LRU sections and try again.
     */
    public long allocate(ByteBuffer sourceBytes) {
        int len = sourceBytes.remaining();
        if (cursor + len > capacityBytes) return -1L;
        ByteBuffer mapped = vertexBuffer.buffer();
        mapped.position((int) cursor);
        mapped.put(sourceBytes);
        long baseVertex = cursor / MeshFormat.VERTEX_BYTES;
        cursor += len;
        return baseVertex;
    }

    public long cursor() { return cursor; }
    public long capacity() { return capacityBytes; }
    public int bufferId() { return vertexBuffer.id(); }

    @Override
    public void close() {
        vertexBuffer.close();
    }
}
