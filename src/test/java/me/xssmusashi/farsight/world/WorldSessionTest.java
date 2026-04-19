package me.xssmusashi.farsight.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorldSessionTest {

    @Test
    void opensAndClosesCleanly(@TempDir Path cacheRoot) {
        WorldSession session = new WorldSession("unit-test", cacheRoot.resolve("world"));
        try {
            assertEquals("unit-test", session.worldId());
            assertNotNull(session.storage());
            assertNotNull(session.ingestor());
        } finally {
            session.close();
        }
    }

    @Test
    void doubleCloseIsHarmless(@TempDir Path cacheRoot) {
        WorldSession session = new WorldSession("two-close", cacheRoot.resolve("world"));
        session.close();
        assertDoesNotThrow(session::close);
    }

    // WorldIdentifier.cacheDirFor() calls FabricLoader.getInstance().getGameDir(),
    // which is unavailable in a unit-test classpath — covered instead by runtime
    // logging on the first JOIN event.
}
