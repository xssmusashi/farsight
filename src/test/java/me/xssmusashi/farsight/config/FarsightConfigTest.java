package me.xssmusashi.farsight.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FarsightConfigTest {

    @Test
    void createsDefaultWhenMissing(@TempDir Path tmp) {
        Path file = tmp.resolve("farsight.json");
        assertFalse(Files.exists(file));
        FarsightConfig cfg = FarsightConfig.loadOrCreate(file);
        assertTrue(cfg.enabled);
        assertEquals(32, cfg.lodRenderDistance);
        assertTrue(Files.exists(file));
    }

    @Test
    void roundTripsChangedValues(@TempDir Path tmp) {
        Path file = tmp.resolve("farsight.json");
        FarsightConfig cfg = FarsightConfig.loadOrCreate(file);
        cfg.lodRenderDistance = 64;
        cfg.useComputeCulling = false;
        cfg.save(file);

        FarsightConfig again = FarsightConfig.loadOrCreate(file);
        assertEquals(64, again.lodRenderDistance);
        assertFalse(again.useComputeCulling);
    }

    @Test
    void fallsBackOnBadJson(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("farsight.json");
        Files.writeString(file, "{ this is not valid json }");
        FarsightConfig cfg = FarsightConfig.loadOrCreate(file);
        assertNotNull(cfg);
        assertTrue(cfg.enabled);  // default
    }
}
