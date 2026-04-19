package me.xssmusashi.farsight.core.lod;

import me.xssmusashi.farsight.core.voxel.Section;

/**
 * Intra-section mip pyramid. Given a native 32³ section, produces 6 levels:
 * <pre>
 *   level 0 : 32³   (native)
 *   level 1 : 16³   (each voxel = 2³ native)
 *   level 2 :  8³
 *   level 3 :  4³
 *   level 4 :  2³
 *   level 5 :  1³   (single voxel representing whole section)
 * </pre>
 *
 * <p>Used as a building block for the cross-section LoD hierarchy — eight
 * adjacent native sections worth of level-1 mips combine into one level-1
 * section that covers the same world volume, and so on up the tree.</p>
 */
public final class LodPyramid {
    public static final int LEVELS = 6;

    private final long[][] levels;

    private LodPyramid(long[][] levels) {
        this.levels = levels;
    }

    /** Immutable snapshot of level {@code L}. */
    public long[] level(int L) {
        return levels[L];
    }

    public int sizeOf(int L) {
        return Section.SIZE >> L;
    }

    public static LodPyramid build(Section native_) {
        long[][] levels = new long[LEVELS][];
        long[] flat = new long[Section.VOLUME];
        for (int i = 0; i < Section.VOLUME; i++) {
            flat[i] = native_.getByIndex(i);
        }
        levels[0] = flat;
        for (int L = 1; L < LEVELS; L++) {
            int prevSize = Section.SIZE >> (L - 1);
            levels[L] = Downsampler.downsampleCube(levels[L - 1], prevSize);
        }
        return new LodPyramid(levels);
    }
}
