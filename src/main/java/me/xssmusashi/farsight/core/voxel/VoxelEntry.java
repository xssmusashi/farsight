package me.xssmusashi.farsight.core.voxel;

/**
 * Packed 64-bit voxel entry.
 *
 * <p>Layout (LSB-first):</p>
 * <pre>
 *   bits  0..23 — blockstate id (24 bits, 16M states)
 *   bits 24..35 — biome id       (12 bits, 4096 biomes)
 *   bits 36..43 — light          ( 8 bits, packed sky:4 + block:4)
 *   bits 44..51 — flags          ( 8 bits, see {@link #FLAG_*})
 *   bits 52..63 — packed normal  (12 bits, 4 bits per axis signed)
 * </pre>
 */
public final class VoxelEntry {
    public static final long AIR = 0L;

    public static final int BLOCKSTATE_BITS = 24;
    public static final int BIOME_BITS = 12;
    public static final int LIGHT_BITS = 8;
    public static final int FLAGS_BITS = 8;
    public static final int NORMAL_BITS = 12;

    public static final int MAX_BLOCKSTATE = (1 << BLOCKSTATE_BITS) - 1;
    public static final int MAX_BIOME = (1 << BIOME_BITS) - 1;

    public static final int FLAG_TRANSLUCENT = 1;
    public static final int FLAG_EMISSIVE = 1 << 1;
    public static final int FLAG_FLUID = 1 << 2;
    public static final int FLAG_PLANT = 1 << 3;
    public static final int FLAG_SOLID = 1 << 4;

    private static final long MASK_BLOCKSTATE = (1L << BLOCKSTATE_BITS) - 1L;
    private static final long MASK_BIOME = (1L << BIOME_BITS) - 1L;
    private static final long MASK_LIGHT = (1L << LIGHT_BITS) - 1L;
    private static final long MASK_FLAGS = (1L << FLAGS_BITS) - 1L;
    private static final long MASK_NORMAL = (1L << NORMAL_BITS) - 1L;

    private static final int SHIFT_BLOCKSTATE = 0;
    private static final int SHIFT_BIOME = BLOCKSTATE_BITS;
    private static final int SHIFT_LIGHT = SHIFT_BIOME + BIOME_BITS;
    private static final int SHIFT_FLAGS = SHIFT_LIGHT + LIGHT_BITS;
    private static final int SHIFT_NORMAL = SHIFT_FLAGS + FLAGS_BITS;

    private VoxelEntry() {}

    public static long encode(int blockstate, int biome, int light, int flags, int normalPacked) {
        return ((blockstate & MASK_BLOCKSTATE) << SHIFT_BLOCKSTATE)
             | ((biome & MASK_BIOME) << SHIFT_BIOME)
             | ((light & MASK_LIGHT) << SHIFT_LIGHT)
             | ((flags & MASK_FLAGS) << SHIFT_FLAGS)
             | ((normalPacked & MASK_NORMAL) << SHIFT_NORMAL);
    }

    public static int blockstate(long entry) { return (int) ((entry >>> SHIFT_BLOCKSTATE) & MASK_BLOCKSTATE); }
    public static int biome(long entry)      { return (int) ((entry >>> SHIFT_BIOME) & MASK_BIOME); }
    public static int light(long entry)      { return (int) ((entry >>> SHIFT_LIGHT) & MASK_LIGHT); }
    public static int flags(long entry)      { return (int) ((entry >>> SHIFT_FLAGS) & MASK_FLAGS); }
    public static int normalPacked(long entry) { return (int) ((entry >>> SHIFT_NORMAL) & MASK_NORMAL); }

    public static int skyLight(long entry)   { return light(entry) & 0xF; }
    public static int blockLight(long entry) { return (light(entry) >>> 4) & 0xF; }

    public static long withBlockstate(long entry, int blockstate) {
        return (entry & ~(MASK_BLOCKSTATE << SHIFT_BLOCKSTATE))
             | ((blockstate & MASK_BLOCKSTATE) << SHIFT_BLOCKSTATE);
    }

    public static boolean isAir(long entry) { return entry == AIR; }
    public static boolean hasFlag(long entry, int flag) { return (flags(entry) & flag) != 0; }
}
