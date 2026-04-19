package me.xssmusashi.farsight.core.storage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * On-disk representation of one section's pre-baked mesh. Vertex payload
 * matches {@link me.xssmusashi.farsight.core.mesh.MeshFormat} exactly
 * (16 bytes per vertex, 4 vertices per quad). The header carries the
 * world-space origin of the section so the render-side loader can compute
 * the AABB without consulting the voxel data again.
 *
 * <p>Layout (little-endian):</p>
 * <pre>
 *   u32 quadCount
 *   i32 originX
 *   i32 originY
 *   i32 originZ
 *   f32 voxelScale           (1.0 for native, 2^L for LoD level L)
 *   u8[quadCount*64] vertex-payload bytes
 * </pre>
 */
public record MeshBlob(int originX, int originY, int originZ, float voxelScale,
                       int quadCount, byte[] vertexBytes) {

    public static final int HEADER_BYTES = 4 + 4 * 3 + 4;

    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES + vertexBytes.length)
            .order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(quadCount);
        buf.putInt(originX);
        buf.putInt(originY);
        buf.putInt(originZ);
        buf.putFloat(voxelScale);
        buf.put(vertexBytes);
        return buf.array();
    }

    public static MeshBlob decode(byte[] bytes) {
        if (bytes.length < HEADER_BYTES) {
            throw new IllegalArgumentException("mesh blob shorter than header");
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int qc = buf.getInt();
        int ox = buf.getInt();
        int oy = buf.getInt();
        int oz = buf.getInt();
        float scale = buf.getFloat();
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        return new MeshBlob(ox, oy, oz, scale, qc, payload);
    }
}
