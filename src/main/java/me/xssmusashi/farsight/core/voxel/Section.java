package me.xssmusashi.farsight.core.voxel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Fixed-size 32×32×32 voxel volume with a palette.
 *
 * <p>Indexing convention is Y-major, Z-middle, X-minor — chosen so that a scan
 * along +X walks contiguous memory, which matches the inner loop of the greedy
 * mesher.</p>
 */
public final class Section {
    public static final int SIZE = 32;
    public static final int SIZE_MASK = SIZE - 1;
    public static final int VOLUME = SIZE * SIZE * SIZE;
    public static final int SIZE_SHIFT = 5;

    private final Palette palette;
    private final short[] indices;

    public Section() {
        this.palette = new Palette();
        this.indices = new short[VOLUME];
    }

    private Section(Palette palette, short[] indices) {
        this.palette = palette;
        this.indices = indices;
    }

    public Palette palette() { return palette; }
    public short[] rawIndices() { return indices; }

    public static int index(int x, int y, int z) {
        return (y << (SIZE_SHIFT + SIZE_SHIFT)) | (z << SIZE_SHIFT) | x;
    }

    public long get(int x, int y, int z) {
        return palette.get(indices[index(x, y, z)] & 0xFFFF);
    }

    public long getByIndex(int i) {
        return palette.get(indices[i] & 0xFFFF);
    }

    public void set(int x, int y, int z, long entry) {
        indices[index(x, y, z)] = (short) palette.add(entry);
    }

    public void setByIndex(int i, long entry) {
        indices[i] = (short) palette.add(entry);
    }

    public boolean isAllAir() {
        if (palette.size() == 1) return true;
        for (short s : indices) {
            if ((s & 0xFFFF) != Palette.AIR_INDEX) return false;
        }
        return true;
    }

    // -- Serialization ---------------------------------------------------------

    /**
     * Serializes to a compact byte form:
     * <pre>
     *   u16 paletteSize
     *   u64[paletteSize] paletteEntries
     *   u16[32768] indices
     * </pre>
     * Total: 2 + 8*paletteSize + 65536 bytes. Designed to be zstd-friendly.
     */
    public byte[] serialize() {
        long[] rawPalette = palette.rawEntries();
        int paletteSize = rawPalette.length;
        int totalLen = 2 + (paletteSize * 8) + (VOLUME * 2);
        ByteBuffer buf = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) paletteSize);
        for (long e : rawPalette) buf.putLong(e);
        for (short s : indices) buf.putShort(s);
        return buf.array();
    }

    public static Section deserialize(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int paletteSize = buf.getShort() & 0xFFFF;
        long[] rawPalette = new long[paletteSize];
        for (int i = 0; i < paletteSize; i++) rawPalette[i] = buf.getLong();
        short[] idx = new short[VOLUME];
        for (int i = 0; i < VOLUME; i++) idx[i] = buf.getShort();
        return new Section(Palette.fromArray(rawPalette), idx);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Section other)) return false;
        return Arrays.equals(this.indices, other.indices)
            && Arrays.equals(this.palette.rawEntries(), other.palette.rawEntries());
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(indices) + Arrays.hashCode(palette.rawEntries());
    }
}
