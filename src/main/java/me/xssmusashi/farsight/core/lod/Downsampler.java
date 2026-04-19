package me.xssmusashi.farsight.core.lod;

import me.xssmusashi.farsight.core.voxel.BlockPriority;
import me.xssmusashi.farsight.core.voxel.VoxelEntry;

/**
 * 2×2×2 voxel reduction by majority vote on blockstate id.
 *
 * <p>Ties are broken by {@link BlockPriority} — solid outranks fluid outranks
 * plant outranks air. The winning input voxel's full packed entry is returned
 * unchanged (biome, light, flags, normal preserved from a representative
 * voxel), not averaged.</p>
 *
 * <p>Independent implementation; inspired by techniques described in
 * voxel-rendering literature.</p>
 */
public final class Downsampler {
    private Downsampler() {}

    /**
     * Majority vote on 8 voxels. Returns the representative entry of the
     * winning blockstate id.
     */
    public static long downsample8(long v0, long v1, long v2, long v3,
                                   long v4, long v5, long v6, long v7) {
        long[] votes = {v0, v1, v2, v3, v4, v5, v6, v7};
        int bestIdx = 0;
        int bestCount = 0;
        int bestPrio = BlockPriority.of(votes[0]);
        for (int i = 0; i < 8; i++) {
            int bs = VoxelEntry.blockstate(votes[i]);
            int count = 0;
            for (int j = 0; j < 8; j++) {
                if (VoxelEntry.blockstate(votes[j]) == bs) count++;
            }
            int prio = BlockPriority.of(votes[i]);
            boolean better =
                count > bestCount
                || (count == bestCount && prio > bestPrio);
            if (better) {
                bestIdx = i;
                bestCount = count;
                bestPrio = prio;
            }
        }
        return votes[bestIdx];
    }

    /**
     * Downsamples a dense cube of voxels (size × size × size) to the next
     * lower mip level (size/2)³. Indexing is y-major, z-middle, x-minor
     * (matching {@link me.xssmusashi.farsight.core.voxel.Section}).
     */
    public static long[] downsampleCube(long[] input, int size) {
        if ((size & 1) != 0 || size < 2) {
            throw new IllegalArgumentException("size must be a positive even int, got " + size);
        }
        if (input.length != size * size * size) {
            throw new IllegalArgumentException(
                "input length " + input.length + " != " + size + "³");
        }
        int half = size / 2;
        long[] out = new long[half * half * half];
        for (int py = 0; py < half; py++) {
            for (int pz = 0; pz < half; pz++) {
                for (int px = 0; px < half; px++) {
                    int cx = px << 1, cy = py << 1, cz = pz << 1;
                    long v0 = input[idx(cx,     cy,     cz,     size)];
                    long v1 = input[idx(cx + 1, cy,     cz,     size)];
                    long v2 = input[idx(cx,     cy,     cz + 1, size)];
                    long v3 = input[idx(cx + 1, cy,     cz + 1, size)];
                    long v4 = input[idx(cx,     cy + 1, cz,     size)];
                    long v5 = input[idx(cx + 1, cy + 1, cz,     size)];
                    long v6 = input[idx(cx,     cy + 1, cz + 1, size)];
                    long v7 = input[idx(cx + 1, cy + 1, cz + 1, size)];
                    out[idx(px, py, pz, half)] = downsample8(v0, v1, v2, v3, v4, v5, v6, v7);
                }
            }
        }
        return out;
    }

    private static int idx(int x, int y, int z, int size) {
        return (y * size + z) * size + x;
    }
}
