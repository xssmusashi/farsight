package me.xssmusashi.farsight.render;

import me.xssmusashi.farsight.FarsightClient;

/**
 * Top-level renderer orchestrator. Lazily initialises GL resources the first
 * time {@link #ensureInitialised()} is called from a thread that owns the GL
 * context; call {@link #close()} from the same thread on shutdown.
 *
 * <p>Phase 5 status: scaffolding only — no mixin hook into Minecraft's render
 * loop yet, so the class does not actually produce pixels. Separately
 * verifying that shaders compile and buffers allocate against a real context
 * requires a running client, which is out of scope for this session.</p>
 */
public final class FarsightRenderer implements AutoCloseable {
    private GpuContext context;
    private ShaderProgram sectionProgram;
    private CullingCompute culling;
    private SectionVboPool vboPool;
    private MdicDrawBuffer drawBuffer;
    private boolean initialised;

    private static final long DEFAULT_VBO_BYTES = 256L * 1024L * 1024L;   // 256 MB
    private static final int  DEFAULT_MAX_DRAWS = 16384;

    public void ensureInitialised() {
        if (initialised) return;
        try {
            context = GpuContext.probe();
            sectionProgram = ShaderProgram.graphics(
                ShaderProgram.loadResource("/assets/farsight/shaders/section.vert"),
                ShaderProgram.loadResource("/assets/farsight/shaders/section.frag"));
            culling = new CullingCompute();
            vboPool = new SectionVboPool(DEFAULT_VBO_BYTES);
            drawBuffer = new MdicDrawBuffer(DEFAULT_MAX_DRAWS);
            initialised = true;
            FarsightClient.LOGGER.info("Farsight renderer initialised");
        } catch (RuntimeException e) {
            FarsightClient.LOGGER.error("renderer init failed", e);
            close();
            throw e;
        }
    }

    public boolean isInitialised() { return initialised; }
    public GpuContext context() { return context; }

    @Override
    public void close() {
        try { if (culling != null) culling.close(); } catch (Exception ignored) {}
        try { if (drawBuffer != null) drawBuffer.close(); } catch (Exception ignored) {}
        try { if (vboPool != null) vboPool.close(); } catch (Exception ignored) {}
        try { if (sectionProgram != null) sectionProgram.close(); } catch (Exception ignored) {}
        initialised = false;
    }
}
