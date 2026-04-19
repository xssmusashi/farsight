package me.xssmusashi.farsight.render;

import me.xssmusashi.farsight.FarsightClient;

/**
 * CPU-side registry of live sections. Previously mirrored a persistent-mapped
 * SSBO consumed by the culling compute shader; now that the compute path is
 * disabled on the MC 3.3 core context, this is a plain array of records
 * that the CPU fallback draw loop iterates directly.
 */
public final class SectionRegistry implements AutoCloseable {

    public record Slot(
        int id,
        float originX, float originY, float originZ,
        float extent, float voxelScale,
        int baseVertex, int indexCount) {}

    private final int capacity;
    private final Slot[] slots;
    private int liveCount;

    public SectionRegistry(int capacity) {
        this.capacity = capacity;
        this.slots = new Slot[capacity];
    }

    public int capacity()  { return capacity; }
    public int liveCount() { return liveCount; }

    public Slot[] snapshot() {
        return slots;  // callers must read indices 0..capacity and skip nulls
    }

    public int register(float originX, float originY, float originZ,
                        float extent, float voxelScale,
                        int baseVertex, int indexCount) {
        int slot = findFreeSlot();
        if (slot < 0) return -1;
        slots[slot] = new Slot(slot, originX, originY, originZ,
            extent, voxelScale, baseVertex, indexCount);
        liveCount++;
        return slot;
    }

    public void unregister(int slot) {
        if (slot < 0 || slot >= capacity) return;
        if (slots[slot] != null) {
            slots[slot] = null;
            liveCount--;
        }
    }

    private int findFreeSlot() {
        for (int i = 0; i < capacity; i++) {
            if (slots[i] == null) return i;
        }
        return -1;
    }

    @Override
    public void close() {
        java.util.Arrays.fill(slots, null);
        liveCount = 0;
        FarsightClient.LOGGER.debug("SectionRegistry closed");
    }
}
