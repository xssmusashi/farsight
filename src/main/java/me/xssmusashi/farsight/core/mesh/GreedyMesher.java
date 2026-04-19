package me.xssmusashi.farsight.core.mesh;

import me.xssmusashi.farsight.core.voxel.Section;
import me.xssmusashi.farsight.core.voxel.VoxelEntry;

/**
 * Greedy voxel mesher — iterates the 6 axis-aligned face-planes of a {@link Section}
 * and merges contiguous, identically-typed faces into the largest possible rectangles.
 *
 * <p>Each of the 3 axes is processed once. For every boundary plane along the
 * axis (including the two outer boundaries at k=0 and k=SIZE) a visibility
 * mask is built and then greedy-merged.</p>
 *
 * <p>Face type encoding in the mask uses a signed integer so that faces with
 * the same blockstate but opposite normals never merge: {@code +(bs+1)} for
 * faces pointing in the +axis direction, {@code -(bs+1)} for -axis, and 0 for
 * no face. This preserves correctness without a second pass per side.</p>
 *
 * <p>Algorithm reference: Mikola Lysenko, "Meshing in a Minecraft Game"
 * (0fps.net). Independent MIT-licensed implementation, inspired by techniques
 * described in voxel-rendering literature.</p>
 */
public final class GreedyMesher {

    /** Runs the mesher over {@code section}, pushing quads to {@code out}.
     *  Returns the number of emitted quads. */
    public int mesh(Section section, MeshOutput out) {
        final int S = Section.SIZE;
        final long[] v = new long[Section.VOLUME];
        for (int i = 0; i < Section.VOLUME; i++) v[i] = section.getByIndex(i);

        int totalQuads = 0;
        final int[] mask = new int[S * S];

        for (int axis = 0; axis < 3; axis++) {
            for (int k = 0; k <= S; k++) {
                buildMask(v, axis, k, S, mask);
                totalQuads += greedyMerge(axis, k, S, mask, out);
            }
        }
        return totalQuads;
    }

    private static void buildMask(long[] v, int axis, int k, int S, int[] mask) {
        for (int vi = 0; vi < S; vi++) {
            for (int ui = 0; ui < S; ui++) {
                long a = (k > 0) ? readVox(v, axis, ui, vi, k - 1, S) : 0L;
                long b = (k < S) ? readVox(v, axis, ui, vi, k, S)     : 0L;
                boolean aSolid = !VoxelEntry.isAir(a);
                boolean bSolid = !VoxelEntry.isAir(b);
                int face;
                if (aSolid && !bSolid) {
                    face = VoxelEntry.blockstate(a) + 1;       // +axis face
                } else if (bSolid && !aSolid) {
                    face = -(VoxelEntry.blockstate(b) + 1);     // -axis face
                } else {
                    face = 0;
                }
                mask[vi * S + ui] = face;
            }
        }
    }

    private static int greedyMerge(int axis, int k, int S, int[] mask, MeshOutput out) {
        int quads = 0;
        for (int vi = 0; vi < S; vi++) {
            for (int ui = 0; ui < S; ui++) {
                int type = mask[vi * S + ui];
                if (type == 0) continue;

                int du = 1;
                while (ui + du < S && mask[vi * S + (ui + du)] == type) du++;

                int dv = 1;
                outer:
                while (vi + dv < S) {
                    for (int di = 0; di < du; di++) {
                        if (mask[(vi + dv) * S + (ui + di)] != type) break outer;
                    }
                    dv++;
                }

                int blockstate = (type > 0) ? type - 1 : -type - 1;
                int side = (type > 0) ? +1 : -1;
                out.addQuad(axis, side, k, ui, vi, du, dv, blockstate);
                quads++;

                for (int dj = 0; dj < dv; dj++) {
                    int row = (vi + dj) * S;
                    for (int di = 0; di < du; di++) mask[row + ui + di] = 0;
                }
            }
        }
        return quads;
    }

    private static long readVox(long[] v, int axis, int ui, int vi, int k, int S) {
        int x, y, z;
        switch (axis) {
            case 0 -> { x = k;  y = ui; z = vi; }
            case 1 -> { x = ui; y = k;  z = vi; }
            case 2 -> { x = ui; y = vi; z = k;  }
            default -> throw new IllegalArgumentException("axis " + axis);
        }
        return v[Section.index(x, y, z)];
    }
}
