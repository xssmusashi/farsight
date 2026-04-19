package me.xssmusashi.farsight.render.shader;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PipelineWatcherTest {

    @Test
    void firstTickReportsChange() {
        AtomicReference<Object> slot = new AtomicReference<>(new Object());
        PipelineWatcher w = new PipelineWatcher(slot::get);
        assertTrue(w.tick());       // null -> first value
        assertFalse(w.tick());      // same reference
    }

    @Test
    void identityChangeTriggersRefresh() {
        AtomicReference<Object> slot = new AtomicReference<>(new Object());
        PipelineWatcher w = new PipelineWatcher(slot::get);
        w.tick();                   // settle

        slot.set(new Object());     // simulated hot-swap — new pipeline instance
        assertTrue(w.tick());
        assertFalse(w.tick());
    }

    @Test
    void nullPipelineTreatedAsIdentity() {
        AtomicReference<Object> slot = new AtomicReference<>(null);
        PipelineWatcher w = new PipelineWatcher(slot::get);
        assertFalse(w.tick());      // null -> null is no change
        slot.set(new Object());
        assertTrue(w.tick());
        slot.set(null);
        assertTrue(w.tick());       // pipeline removed
    }

    @Test
    void supplierExceptionDoesNotPropagate() {
        PipelineWatcher w = new PipelineWatcher(() -> {
            throw new RuntimeException("boom");
        });
        assertFalse(w.tick());
        assertFalse(w.tick());
    }

    @Test
    void defaultInstanceWithoutIrisReturnsFalse() {
        PipelineWatcher w = PipelineWatcher.defaultInstance();
        assertFalse(w.tick(), "no Iris on test classpath — no pipeline to detect");
        assertNull(w.lastPipeline());
    }
}
