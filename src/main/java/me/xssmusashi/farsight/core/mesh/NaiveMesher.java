package me.xssmusashi.farsight.core.mesh;

import me.xssmusashi.farsight.core.voxel.Section;
import me.xssmusashi.farsight.core.voxel.VoxelEntry;

/**
 * Baseline mesher: one 1×1 quad per visible voxel face. Used as the A/B
 * comparison target for the greedy mesher benchmark. Correct but wasteful —
 * produces up to 6 × (surface-voxel count) quads.
 */
public final class NaiveMesher {
    public int mesh(Section section, MeshOutput out) {
        int quads = 0;
        int S = Section.SIZE;
        long[] v = new long[Section.VOLUME];
        for (int i = 0; i < Section.VOLUME; i++) v[i] = section.getByIndex(i);

        for (int y = 0; y < S; y++) {
            for (int z = 0; z < S; z++) {
                for (int x = 0; x < S; x++) {
                    long cur = v[Section.index(x, y, z)];
                    if (VoxelEntry.isAir(cur)) continue;
                    int bs = VoxelEntry.blockstate(cur);
                    quads += emitIfVisible(out, v, x, y, z, -1, 0, 0, 0, bs);  // -X
                    quads += emitIfVisible(out, v, x, y, z, +1, 0, 0, 0, bs);  // +X
                    quads += emitIfVisible(out, v, x, y, z, 0, -1, 0, 1, bs);  // -Y
                    quads += emitIfVisible(out, v, x, y, z, 0, +1, 0, 1, bs);  // +Y
                    quads += emitIfVisible(out, v, x, y, z, 0, 0, -1, 2, bs);  // -Z
                    quads += emitIfVisible(out, v, x, y, z, 0, 0, +1, 2, bs);  // +Z
                }
            }
        }
        return quads;
    }

    private int emitIfVisible(MeshOutput out, long[] v,
                              int x, int y, int z,
                              int dx, int dy, int dz,
                              int axis, int bs) {
        int S = Section.SIZE;
        int nx = x + dx, ny = y + dy, nz = z + dz;
        boolean outside = nx < 0 || nx >= S || ny < 0 || ny >= S || nz < 0 || nz >= S;
        long neighbor = outside ? 0L : v[Section.index(nx, ny, nz)];
        if (!VoxelEntry.isAir(neighbor)) return 0;
        int side = (dx + dy + dz) > 0 ? +1 : -1;
        int planeK = switch (axis) {
            case 0 -> side > 0 ? x + 1 : x;
            case 1 -> side > 0 ? y + 1 : y;
            case 2 -> side > 0 ? z + 1 : z;
            default -> 0;
        };
        int u = switch (axis) { case 0 -> y; case 1 -> x; case 2 -> x; default -> 0; };
        int vv = switch (axis) { case 0 -> z; case 1 -> z; case 2 -> y; default -> 0; };
        out.addQuad(axis, side, planeK, u, vv, 1, 1, bs);
        return 1;
    }
}
