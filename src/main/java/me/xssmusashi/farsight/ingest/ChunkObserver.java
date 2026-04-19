package me.xssmusashi.farsight.ingest;

import me.xssmusashi.farsight.FarsightClient;
import me.xssmusashi.farsight.core.storage.SectionKey;
import me.xssmusashi.farsight.core.voxel.Section;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Hooks {@link ClientChunkEvents#CHUNK_LOAD} and converts each MC chunk
 * section into a Farsight {@link me.xssmusashi.farsight.ingest.ChunkSnapshot}.
 *
 * <p><b>Scope note.</b> MC chunks are 16³ per section; Farsight sections are
 * 32³. This first pass maps one MC section to one Farsight section, occupying
 * only the lower-left 16×16×16 octant — the remainder stays air. That
 * produces sparse Farsight sections visually, but it exercises the full
 * ingest + mesh + LMDB pipeline on real data. Aggregating 2×2×2 MC sections
 * into a fully-populated Farsight section lives on top of
 * {@link me.xssmusashi.farsight.core.lod.CrossSectionDownsampler} in a
 * follow-up.</p>
 */
public final class ChunkObserver {
    private ChunkObserver() {}

    public static void register() {
        ClientChunkEvents.CHUNK_LOAD.register(ChunkObserver::onChunkLoad);
    }

    private static void onChunkLoad(ClientLevel level, LevelChunk chunk) {
        me.xssmusashi.farsight.ingest.ChunkIngestor ingestor = FarsightClient.ACTIVE_INGESTOR.get();
        if (ingestor == null) return;

        try {
            ChunkPos pos = chunk.getPos();
            int chunkX = pos.getMinBlockX() >> 4;
            int chunkZ = pos.getMinBlockZ() >> 4;
            int dim = dimensionId(level);
            LevelChunkSection[] sections = chunk.getSections();
            int minSectionY = chunk.getMinSectionY();

            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection s = sections[i];
                if (s == null || s.hasOnlyAir()) continue;
                int sectionY = minSectionY + i;

                long[] voxels = new long[Section.VOLUME];
                boolean anyNonAir = false;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState bs = s.getBlockState(x, y, z);
                            long v = BlockStateMapper.toVoxel(bs);
                            if (v != 0L) anyNonAir = true;
                            voxels[Section.index(x, y, z)] = v;
                        }
                    }
                }
                if (!anyNonAir) continue;

                SectionKey key = new SectionKey(dim, chunkX, sectionY, chunkZ, 0);
                ingestor.submit(new ChunkSnapshot(key, voxels));
            }
        } catch (Throwable t) {
            FarsightClient.LOGGER.warn("chunk ingest failed for {}", chunk.getPos(), t);
        }
    }

    private static int dimensionId(ClientLevel level) {
        try {
            // ResourceKey.toString() is stable across versions; hashing gives a
            // 32-bit per-dimension id that fits the SectionKey format.
            return level.dimension().toString().hashCode();
        } catch (Throwable t) {
            return 0;
        }
    }
}
