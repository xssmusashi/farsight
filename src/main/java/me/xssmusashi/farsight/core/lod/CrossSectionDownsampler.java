package me.xssmusashi.farsight.core.lod;

import me.xssmusashi.farsight.core.voxel.Section;
import me.xssmusashi.farsight.core.voxel.VoxelEntry;

/**
 * Combines eight adjacent sections at LoD level {@code L} into a single
 * section at level {@code L+1} covering twice the volume per axis.
 *
 * <p>The eight children are arranged as a 2×2×2 octant grid; octant index
 * is computed from {@code (ox, oy, oz) ∈ {0,1}³}. A {@code null} child is
 * treated as fully air so partial cubes at world edges still produce a
 * sensible parent.</p>
 *
 * <p>Implementation detail: children are materialised into a dense 64³
 * long-array and handed to {@link Downsampler#downsampleCube} — reusing the
 * intra-section mip path also used by {@link LodPyramid}. Keeps the majority-
 * vote + priority-tie-break rules in one place.</p>
 */
public final class CrossSectionDownsampler {
    public static final int CHILD_COUNT = 8;

    private CrossSectionDownsampler() {}

    public static int octantIndex(int ox, int oy, int oz) {
        return (oy << 2) | (oz << 1) | ox;
    }

    public static Section combine(Section[] children) {
        if (children.length != CHILD_COUNT) {
            throw new IllegalArgumentException("expected 8 children, got " + children.length);
        }
        final int childSize = Section.SIZE;
        final int parentCover = childSize * 2;
        long[] dense = new long[parentCover * parentCover * parentCover];
        // Dense layout: y-major, z-middle, x-minor (matches Downsampler idx)
        for (int oy = 0; oy < 2; oy++) {
            for (int oz = 0; oz < 2; oz++) {
                for (int ox = 0; ox < 2; ox++) {
                    Section child = children[octantIndex(ox, oy, oz)];
                    if (child == null) continue;
                    for (int cy = 0; cy < childSize; cy++) {
                        for (int cz = 0; cz < childSize; cz++) {
                            for (int cx = 0; cx < childSize; cx++) {
                                int dx = cx + ox * childSize;
                                int dy = cy + oy * childSize;
                                int dz = cz + oz * childSize;
                                int denseIdx = (dy * parentCover + dz) * parentCover + dx;
                                dense[denseIdx] = child.get(cx, cy, cz);
                            }
                        }
                    }
                }
            }
        }
        long[] downsampled = Downsampler.downsampleCube(dense, parentCover);
        Section parent = new Section();
        for (int i = 0; i < Section.VOLUME; i++) {
            long v = downsampled[i];
            if (!VoxelEntry.isAir(v)) parent.setByIndex(i, v);
        }
        return parent;
    }
}
