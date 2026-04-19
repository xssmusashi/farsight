package me.xssmusashi.farsight.core.voxel;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Per-section palette mapping {@link VoxelEntry} longs to compact short indices.
 * Entry 0 is reserved for {@link VoxelEntry#AIR}, which is the default state.
 */
public final class Palette {
    public static final int MAX_ENTRIES = 4096;
    public static final int AIR_INDEX = 0;

    private long[] entries;
    private int size;
    private final HashMap<Long, Integer> lookup;

    public Palette() {
        this.entries = new long[16];
        this.entries[0] = VoxelEntry.AIR;
        this.size = 1;
        this.lookup = new HashMap<>(32);
        this.lookup.put(VoxelEntry.AIR, AIR_INDEX);
    }

    public int size() { return size; }

    public long get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("palette index " + index + " / " + size);
        }
        return entries[index];
    }

    public int add(long entry) {
        Integer existing = lookup.get(entry);
        if (existing != null) return existing;
        if (size >= MAX_ENTRIES) {
            throw new IllegalStateException("palette overflow (max " + MAX_ENTRIES + ")");
        }
        if (size >= entries.length) {
            int newLen = Math.min(entries.length * 2, MAX_ENTRIES);
            entries = Arrays.copyOf(entries, newLen);
        }
        int idx = size++;
        entries[idx] = entry;
        lookup.put(entry, idx);
        return idx;
    }

    public int indexOf(long entry) {
        Integer existing = lookup.get(entry);
        return existing == null ? -1 : existing;
    }

    /** Snapshot of palette contents; caller must not mutate. */
    public long[] rawEntries() {
        return Arrays.copyOf(entries, size);
    }

    public static Palette fromArray(long[] rawEntries) {
        Palette p = new Palette();
        p.entries = Arrays.copyOf(rawEntries, Math.max(rawEntries.length, 1));
        p.size = rawEntries.length;
        p.lookup.clear();
        for (int i = 0; i < p.size; i++) {
            p.lookup.put(p.entries[i], i);
        }
        return p;
    }

    /** Bits per index required to address every palette entry (minimum 1). */
    public int bitsPerIndex() {
        if (size <= 1) return 1;
        return 32 - Integer.numberOfLeadingZeros(size - 1);
    }
}
