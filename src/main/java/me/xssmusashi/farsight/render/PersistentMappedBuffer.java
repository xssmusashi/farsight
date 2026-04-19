package me.xssmusashi.farsight.render;

import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.GL46;

import java.nio.ByteBuffer;

/**
 * Persistent + coherent mapped GL buffer. Write from the CPU, read from the
 * GPU, no explicit flush needed. Used for the mega vertex buffer and the
 * indirect-command stream.
 */
public final class PersistentMappedBuffer implements AutoCloseable {
    private final int id;
    private final long sizeBytes;
    private final int target;
    private final ByteBuffer mapped;

    public PersistentMappedBuffer(int target, long sizeBytes) {
        this.target = target;
        this.sizeBytes = sizeBytes;
        this.id = GL46.glGenBuffers();
        GL46.glBindBuffer(target, id);
        int flags = GL44.GL_MAP_WRITE_BIT | GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT;
        GL44.glBufferStorage(target, sizeBytes, flags);
        this.mapped = GL44.glMapBufferRange(target, 0L, sizeBytes, flags);
        if (this.mapped == null) {
            throw new IllegalStateException("glMapBufferRange returned null (size=" + sizeBytes + ")");
        }
    }

    public int id() { return id; }
    public long sizeBytes() { return sizeBytes; }
    public ByteBuffer buffer() { return mapped; }
    public int target() { return target; }

    public void bind() { GL46.glBindBuffer(target, id); }

    @Override
    public void close() {
        GL46.glBindBuffer(target, id);
        GL46.glUnmapBuffer(target);
        GL46.glDeleteBuffers(id);
    }
}
