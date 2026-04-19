package me.xssmusashi.farsight.ingest;

import java.util.concurrent.atomic.AtomicLong;

public final class IngestStats {
    public final AtomicLong sectionsIngested = new AtomicLong();
    public final AtomicLong bytesWritten = new AtomicLong();
    public final AtomicLong quadsProduced = new AtomicLong();
    public final AtomicLong errors = new AtomicLong();

    public String summary() {
        return String.format(
            "sections=%d bytes=%d quads=%d errors=%d",
            sectionsIngested.get(),
            bytesWritten.get(),
            quadsProduced.get(),
            errors.get());
    }
}
