package me.xssmusashi.farsight.render;

import me.xssmusashi.farsight.core.mesh.MeshFormat;
import org.lwjgl.opengl.GL46;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Static {@code GL_ELEMENT_ARRAY_BUFFER} shared by every section draw. Every
 * section's mesh references the same index pattern (quad → two triangles);
 * {@code baseVertex} on the indirect draw command shifts the reads into
 * that section's block of the mega VBO, so one IBO works for all of them.
 */
public final class QuadIndexBuffer implements AutoCloseable {
    public static final int DEFAULT_MAX_QUADS = 4096;

    private final int id;
    private final int maxQuads;

    public QuadIndexBuffer(int maxQuads) {
        this.maxQuads = maxQuads;
        this.id = GL46.glGenBuffers();

        int[] pattern = MeshFormat.buildQuadIndexPattern(maxQuads);
        ByteBuffer buf = ByteBuffer.allocateDirect(pattern.length * Integer.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN);
        for (int idx : pattern) buf.putInt(idx);
        buf.flip();

        GL46.glBindBuffer(GL46.GL_ELEMENT_ARRAY_BUFFER, id);
        GL46.glBufferData(GL46.GL_ELEMENT_ARRAY_BUFFER, buf, GL46.GL_STATIC_DRAW);
    }

    public int id() { return id; }
    public int maxQuads() { return maxQuads; }
    public int maxIndices() { return maxQuads * MeshFormat.INDICES_PER_QUAD; }

    @Override
    public void close() {
        GL46.glDeleteBuffers(id);
    }
}
