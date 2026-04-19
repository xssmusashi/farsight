package me.xssmusashi.farsight.ingest;

import me.xssmusashi.farsight.core.storage.LmdbStorage;
import me.xssmusashi.farsight.core.storage.SectionCodec;
import me.xssmusashi.farsight.core.storage.SectionKey;
import me.xssmusashi.farsight.core.voxel.Section;
import me.xssmusashi.farsight.core.voxel.VoxelEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class ChunkIngestorTest {

    @Test
    void synchronousIngestPersistsSection(@TempDir Path tmp) {
        long[] voxels = synthesisedVoxels();
        SectionKey key = new SectionKey(0, 0, 0, 0, 0);
        ChunkSnapshot snap = new ChunkSnapshot(key, voxels);

        try (LmdbStorage store = new LmdbStorage(tmp, 1L << 28);
             ChunkIngestor ing = new ChunkIngestor(store)) {
            ing.process(snap);

            byte[] stored = store.get(key);
            assertNotNull(stored);
            Section back = Section.deserialize(SectionCodec.decode(stored));
            assertEquals(snap.toSection(), back);

            assertEquals(1, ing.stats().sectionsIngested.get());
            assertTrue(ing.stats().bytesWritten.get() > 0);
            assertTrue(ing.stats().quadsProduced.get() > 0);
        }
    }

    @Test
    void asyncIngestPersistsSection(@TempDir Path tmp) throws ExecutionException, InterruptedException {
        long[] voxels = synthesisedVoxels();
        SectionKey key = new SectionKey(0, 7, 2, -3, 0);
        ChunkSnapshot snap = new ChunkSnapshot(key, voxels);

        try (LmdbStorage store = new LmdbStorage(tmp, 1L << 28);
             ChunkIngestor ing = new ChunkIngestor(store)) {
            ing.submit(snap).get();
            byte[] stored = store.get(key);
            assertNotNull(stored);
        }
    }

    @Test
    void rejectsVoxelArrayOfWrongSize() {
        assertThrows(IllegalArgumentException.class,
            () -> new ChunkSnapshot(new SectionKey(0, 0, 0, 0, 0), new long[42]));
    }

    private static long[] synthesisedVoxels() {
        long stone = VoxelEntry.encode(1, 0, 0, VoxelEntry.FLAG_SOLID, 0);
        long[] v = new long[Section.VOLUME];
        // lower half of the section filled with stone
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < Section.SIZE; z++) {
                for (int x = 0; x < Section.SIZE; x++) {
                    v[Section.index(x, y, z)] = stone;
                }
            }
        }
        return v;
    }
}
