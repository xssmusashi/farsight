package me.xssmusashi.farsight.ingest;

import me.xssmusashi.farsight.core.voxel.VoxelEntry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Translates a Minecraft {@link BlockState} into a packed Farsight voxel
 * entry. Keeps the mapping minimal for the first ingest pass: vanilla
 * blockstate-registry id (truncated to 24 bits), flags derived from the
 * state's high-level behaviour, no lighting or biome tint yet.
 *
 * <p>Truncation is acceptable because vanilla MC 26.1.1 currently has
 * ~21 000 block states (well under {@code 2^24}). Modded environments
 * with millions of states will collide — a future pass will swap in a
 * mod-provided registry view.</p>
 */
public final class BlockStateMapper {
    private BlockStateMapper() {}

    public static long toVoxel(BlockState state) {
        return toVoxel(state, 0);
    }

    public static long toVoxel(BlockState state, int biomeId) {
        if (state.isAir()) return VoxelEntry.AIR;

        int rawId = Block.BLOCK_STATE_REGISTRY.getId(state);
        int stateId = rawId & VoxelEntry.MAX_BLOCKSTATE;

        int flags = 0;
        if (!state.getFluidState().isEmpty()) {
            flags |= VoxelEntry.FLAG_FLUID;
        } else if (state.isSolid()) {
            flags |= VoxelEntry.FLAG_SOLID;
        } else {
            flags |= VoxelEntry.FLAG_PLANT;
        }

        return VoxelEntry.encode(stateId, biomeId & VoxelEntry.MAX_BIOME, 0, flags, 0);
    }
}
