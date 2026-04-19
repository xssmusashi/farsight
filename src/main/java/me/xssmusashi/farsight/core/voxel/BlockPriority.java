package me.xssmusashi.farsight.core.voxel;

/**
 * Priority used when downsampling 2×2×2 voxel blocks to a single LoD voxel:
 * ties in the majority vote are broken by choosing the voxel with highest
 * priority. Rationale — from a distant observer, a block of mostly air with
 * one stone cube still reads as "something solid there", so solids outrank
 * fluids outrank plants outrank air.
 */
public final class BlockPriority {
    public static final int AIR = 0;
    public static final int OTHER = 1;
    public static final int PLANT = 2;
    public static final int FLUID = 3;
    public static final int SOLID = 4;

    private BlockPriority() {}

    public static int of(long entry) {
        if (VoxelEntry.isAir(entry)) return AIR;
        int flags = VoxelEntry.flags(entry);
        if ((flags & VoxelEntry.FLAG_SOLID) != 0) return SOLID;
        if ((flags & VoxelEntry.FLAG_FLUID) != 0) return FLUID;
        if ((flags & VoxelEntry.FLAG_PLANT) != 0) return PLANT;
        return OTHER;
    }
}
