package me.xssmusashi.farsight.core.mesh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Accumulates quad vertices into a direct {@link ByteBuffer} using the
 * {@link MeshFormat} layout. Grows automatically. Not thread-safe — one
 * builder per worker.
 */
public final class MeshBuilder implements MeshOutput {
    private ByteBuffer buffer;
    private int quadCount;

    public MeshBuilder() {
        this(4096);
    }

    public MeshBuilder(int initialCapacityBytes) {
        this.buffer = ByteBuffer.allocateDirect(initialCapacityBytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    public int quadCount() { return quadCount; }
    public int vertexByteCount() { return buffer.position(); }

    /** Rewinds; returns a read-only view of the accumulated bytes. */
    public ByteBuffer snapshot() {
        ByteBuffer view = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        view.flip();
        return view.asReadOnlyBuffer();
    }

    public void clear() {
        buffer.clear();
        quadCount = 0;
    }

    @Override
    public void addQuad(int axis, int side, int planeK, int u, int v, int du, int dv, int blockstate) {
        ensureCapacity(MeshFormat.QUAD_BYTES);
        int face = MeshFormat.faceIndex(axis, side);
        // Corner positions in section space
        int[][] corners = corners(axis, planeK, u, v, du, dv);
        int packedState = MeshFormat.packStateFlags(blockstate, 0);
        for (int[] c : corners) {
            int packedPos = MeshFormat.packPosNormal(c[0], c[1], c[2], face);
            int packedAo = MeshFormat.packAoUv(255, 0, 0);
            buffer.putInt(packedPos);
            buffer.putInt(packedAo);
            buffer.putInt(packedState);
            buffer.putInt(0);
        }
        quadCount++;
    }

    private static int[][] corners(int axis, int k, int u, int v, int du, int dv) {
        int[][] c = new int[4][3];
        switch (axis) {
            case 0 -> {
                c[0] = new int[]{k, u,       v};
                c[1] = new int[]{k, u + du,  v};
                c[2] = new int[]{k, u + du,  v + dv};
                c[3] = new int[]{k, u,       v + dv};
            }
            case 1 -> {
                c[0] = new int[]{u,      k, v};
                c[1] = new int[]{u + du, k, v};
                c[2] = new int[]{u + du, k, v + dv};
                c[3] = new int[]{u,      k, v + dv};
            }
            case 2 -> {
                c[0] = new int[]{u,       v,      k};
                c[1] = new int[]{u + du,  v,      k};
                c[2] = new int[]{u + du,  v + dv, k};
                c[3] = new int[]{u,       v + dv, k};
            }
            default -> throw new IllegalArgumentException("axis " + axis);
        }
        return c;
    }

    private void ensureCapacity(int extra) {
        if (buffer.remaining() >= extra) return;
        int newCap = Math.max(buffer.capacity() * 2, buffer.position() + extra);
        ByteBuffer grown = ByteBuffer.allocateDirect(newCap).order(ByteOrder.LITTLE_ENDIAN);
        buffer.flip();
        grown.put(buffer);
        buffer = grown;
    }
}
