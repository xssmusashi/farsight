package me.xssmusashi.farsight.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FilesystemStorageTest {

    @Test
    void putGetDelete(@TempDir Path tmp) {
        try (FilesystemStorage store = new FilesystemStorage(tmp)) {
            SectionKey k = new SectionKey(0, 1, 2, 3, 0);
            byte[] value = {1, 2, 3, 4};
            assertNull(store.get(k));
            store.put(k, value);
            assertArrayEquals(value, store.get(k));
            assertTrue(store.delete(k));
            assertNull(store.get(k));
        }
    }

    @Test
    void sectionsAndMeshesAreSeparate(@TempDir Path tmp) {
        try (FilesystemStorage store = new FilesystemStorage(tmp)) {
            SectionKey k = new SectionKey(0, 0, 0, 0, 0);
            store.put(k, new byte[]{9, 9});
            store.putMesh(k, new byte[]{7, 7, 7});
            assertArrayEquals(new byte[]{9, 9}, store.get(k));
            assertArrayEquals(new byte[]{7, 7, 7}, store.getMesh(k));
        }
    }

    @Test
    void surviveReopen(@TempDir Path tmp) {
        SectionKey k = new SectionKey(0, 4, 5, 6, 0);
        byte[] payload = new byte[2048];
        new Random(7).nextBytes(payload);
        try (FilesystemStorage store = new FilesystemStorage(tmp)) {
            store.put(k, payload);
        }
        try (FilesystemStorage store = new FilesystemStorage(tmp)) {
            assertArrayEquals(payload, store.get(k));
            assertEquals(1, store.approximateEntryCount());
        }
    }
}
