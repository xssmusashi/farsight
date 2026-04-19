package me.xssmusashi.farsight.ingest;

import me.xssmusashi.farsight.core.storage.SectionKey;
import org.jctools.queues.MpscArrayQueue;

/**
 * Bridges the ingest ForkJoinPool (multiple producers) and the render thread
 * (single consumer) without sharing object references across module
 * boundaries. Producers call {@link #publish} after a section's mesh has
 * been written to LMDB; the renderer drains it each frame in
 * {@code SectionLoader#tick}.
 *
 * <p>Lockfree MPSC queue from JCTools. Capacity is generous (64k) — drops
 * silently when full, which just means the render-side loader falls
 * slightly behind reality and the section becomes visible on the next pass
 * through the pending set.</p>
 */
public final class PendingSections {
    public static final MpscArrayQueue<SectionKey> QUEUE = new MpscArrayQueue<>(65_536);

    private PendingSections() {}

    public static void publish(SectionKey key) {
        QUEUE.offer(key);
    }

    public static SectionKey poll() {
        return QUEUE.poll();
    }
}
