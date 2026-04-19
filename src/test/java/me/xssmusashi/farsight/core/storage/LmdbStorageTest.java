package me.xssmusashi.farsight.core.storage;

import me.xssmusashi.farsight.core.voxel.Section;
import me.xssmusashi.farsight.core.voxel.VoxelEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LmdbStorageTest {

    @Test
    void codecRoundtrip() {
        byte[] payload = new byte[65_536];
        new Random(1L).nextBytes(payload);
        byte[] framed = SectionCodec.encode(payload);
        byte[] back = SectionCodec.decode(framed);
        assertArrayEquals(payload, back);
    }

    @Test
    void keyRoundtrip() {
        SectionKey k = new SectionKey(0, -17, 3, 9001, 4);
        byte[] bytes = k.toBytes();
        assertEquals(SectionKey.BYTES, bytes.length);
        SectionKey back = SectionKey.fromBytes(bytes);
        assertEquals(k, back);
    }

    @Test
    void lmdbPutGetDelete(@TempDir Path tmp) {
        try (LmdbStorage store = new LmdbStorage(tmp, 1L << 28)) {
            SectionKey k = new SectionKey(0, 4, 2, 8, 0);
            byte[] value = {1, 2, 3, 4, 5};
            assertNull(store.get(k));
            store.put(k, value);
            assertArrayEquals(value, store.get(k));
            assertTrue(store.delete(k));
            assertNull(store.get(k));
        }
    }

    @Test
    void lmdbPersistsFullSection(@TempDir Path tmp) {
        Section s = new Section();
        Random rnd = new Random(0xC0FFEEL);
        long stone = VoxelEntry.encode(1, 0, 0, VoxelEntry.FLAG_SOLID, 0);
        long dirt = VoxelEntry.encode(2, 0, 0, VoxelEntry.FLAG_SOLID, 0);
        for (int i = 0; i < Section.VOLUME; i++) {
            float p = rnd.nextFloat();
            if (p < 0.3f) s.setByIndex(i, stone);
            else if (p < 0.5f) s.setByIndex(i, dirt);
        }

        byte[] raw = s.serialize();
        byte[] framed = SectionCodec.encode(raw);
        assertTrue(framed.length < raw.length, "zstd should compress voxel data");

        SectionKey k = new SectionKey(0, 12, 3, -5, 0);
        try (LmdbStorage store = new LmdbStorage(tmp, 1L << 28)) {
            store.put(k, framed);
            byte[] got = store.get(k);
            assertArrayEquals(framed, got);
            byte[] decoded = SectionCodec.decode(got);
            Section round = Section.deserialize(decoded);
            assertEquals(s, round);
        }
    }
}
