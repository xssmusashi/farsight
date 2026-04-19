package me.xssmusashi.farsight.render;

import me.xssmusashi.farsight.FarsightClient;
import org.lwjgl.opengl.GL46;

import java.nio.ByteBuffer;
import java.util.BitSet;

/**
 * GPU-resident table of per-section data. One {@code SectionRecord} per
 * live section, laid out to match both {@code culling.comp}'s input and
 * {@code section.vert}'s origin lookup:
 *
 * <pre>
 *   struct SectionRecord {
 *       vec4 aabbMin;   // xyz = world origin, w = voxelScale
 *       vec4 aabbMax;   // xyz = world origin + extent, w = (floatBitsToUint) baseVertex
 *   };
 * </pre>
 *
 * <p>Backed by a persistent-mapped SSBO so updates from the render thread
 * are visible to the GPU without explicit flushes. Slot allocation uses a
 * compact free-list {@link BitSet}; records are never compacted so
 * {@code gl_BaseInstance} (set by the culling compute output) remains a
 * stable identity for the shader.</p>
 */
public final class SectionRegistry implements AutoCloseable {
    public static final int RECORD_BYTES = 32;            // 2 × vec4

    private final int capacity;
    private final PersistentMappedBuffer ssbo;
    private final BitSet occupied;
    private int highWater;

    public SectionRegistry(int capacity) {
        this.capacity = capacity;
        this.ssbo = new PersistentMappedBuffer(GL46.GL_SHADER_STORAGE_BUFFER,
            (long) capacity * RECORD_BYTES);
        this.occupied = new BitSet(capacity);
    }

    public int capacity()         { return capacity; }
    public int highWaterMark()    { return highWater; }
    public int ssboId()           { return ssbo.id(); }

    /**
     * Registers a new section. Returns the slot index (stable identity for
     * {@code gl_BaseInstance} in the culling output), or -1 if the registry
     * is full.
     */
    public int register(float originX, float originY, float originZ,
                        float extent, float voxelScale, int baseVertex) {
        int slot = findFreeSlot();
        if (slot < 0) return -1;
        occupied.set(slot);
        if (slot >= highWater) highWater = slot + 1;
        writeRecord(slot, originX, originY, originZ, extent, voxelScale, baseVertex);
        return slot;
    }

    public void unregister(int slot) {
        if (slot < 0 || slot >= capacity) return;
        occupied.clear(slot);
        ByteBuffer buf = ssbo.buffer();
        int offset = slot * RECORD_BYTES;
        for (int i = 0; i < RECORD_BYTES; i++) buf.put(offset + i, (byte) 0);
    }

    private int findFreeSlot() {
        int next = occupied.nextClearBit(0);
        if (next >= capacity) return -1;
        return next;
    }

    private void writeRecord(int slot, float originX, float originY, float originZ,
                             float extent, float voxelScale, int baseVertex) {
        ByteBuffer buf = ssbo.buffer();
        int o = slot * RECORD_BYTES;
        buf.putFloat(o,      originX);
        buf.putFloat(o + 4,  originY);
        buf.putFloat(o + 8,  originZ);
        buf.putFloat(o + 12, voxelScale);
        buf.putFloat(o + 16, originX + extent);
        buf.putFloat(o + 20, originY + extent);
        buf.putFloat(o + 24, originZ + extent);
        buf.putInt  (o + 28, baseVertex);   // reinterpret as float on GPU via floatBitsToUint
    }

    @Override
    public void close() {
        try { ssbo.close(); } catch (Exception e) {
            FarsightClient.LOGGER.debug("SectionRegistry ssbo close threw", e);
        }
    }
}
