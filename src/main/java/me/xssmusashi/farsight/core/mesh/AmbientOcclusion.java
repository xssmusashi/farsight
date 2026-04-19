package me.xssmusashi.farsight.core.mesh;

import me.xssmusashi.farsight.core.voxel.Section;
import me.xssmusashi.farsight.core.voxel.VoxelEntry;

/**
 * Simple per-face ambient occlusion. For a face at plane {@code planeK} of
 * {@code axis}, looks at the 8 voxels in the 3×3 neighbourhood of the voxel
 * immediately on the air side of the face; returns a darkness value in
 * {@code [0, 255]} where 255 is fully lit and 0 is fully occluded.
 *
 * <p>This is intentionally coarse (uniform across a merged quad). It trades
 * per-vertex fidelity for keeping the greedy merge runs long. A per-vertex
 * AO pass with quad-splitting can be added later without changing callers
 * of this class.</p>
 */
public final class AmbientOcclusion {
    public static final int FULL_LIGHT = 255;
    public static final int PER_NEIGHBOUR_DARKEN = 25;

    private AmbientOcclusion() {}

    public static int perFace(long[] voxels, int axis, int side, int planeK, int u, int v) {
        int aboveK = (side > 0) ? planeK : planeK - 1;
        if (aboveK < 0 || aboveK >= Section.SIZE) return FULL_LIGHT;

        int solidCount = 0;
        for (int du = -1; du <= 1; du++) {
            for (int dv = -1; dv <= 1; dv++) {
                if (du == 0 && dv == 0) continue;
                int nu = u + du;
                int nv = v + dv;
                if (nu < 0 || nu >= Section.SIZE || nv < 0 || nv >= Section.SIZE) continue;
                int x, y, z;
                switch (axis) {
                    case 0 -> { x = aboveK; y = nu; z = nv; }
                    case 1 -> { x = nu; y = aboveK; z = nv; }
                    case 2 -> { x = nu; y = nv; z = aboveK; }
                    default -> throw new IllegalArgumentException("axis " + axis);
                }
                if (!VoxelEntry.isAir(voxels[Section.index(x, y, z)])) solidCount++;
            }
        }
        return Math.max(0, FULL_LIGHT - solidCount * PER_NEIGHBOUR_DARKEN);
    }
}
