package me.xssmusashi.farsight.render;

import me.xssmusashi.farsight.FarsightClient;
import me.xssmusashi.farsight.render.shader.IrisAdapter;
import me.xssmusashi.farsight.render.shader.IrisCompatibility;
import me.xssmusashi.farsight.render.shader.PipelineWatcher;

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
    private IrisAdapter irisAdapter;
    private PipelineWatcher pipelineWatcher;
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
            pipelineWatcher = PipelineWatcher.defaultInstance();
            refreshIrisAdapter();
            initialised = true;
            FarsightClient.LOGGER.info(
                "Farsight renderer initialised (iris={}, pack={})",
                IrisCompatibility.get().isInstalled(),
                IrisCompatibility.get().activeShaderPackName());
        } catch (RuntimeException e) {
            FarsightClient.LOGGER.error("renderer init failed", e);
            close();
            throw e;
        }
    }

    /**
     * Re-queries the Iris adapter; call on shader pack hot-swap. Cheap — only
     * flips an enum-like object reference.
     */
    public void refreshIrisAdapter() {
        this.irisAdapter = IrisAdapter.resolve();
        FarsightClient.LOGGER.info("Iris adapter refreshed → active={}", irisAdapter.isActive());
    }

    public IrisAdapter irisAdapter() { return irisAdapter; }
    public PipelineWatcher pipelineWatcher() { return pipelineWatcher; }

    /**
     * Per-frame hook — detects shader-pack reload and refreshes the adapter,
     * then binds whatever draw-buffer Farsight should target. Safe to call
     * before {@link #ensureInitialised()} has completed (it's a no-op).
     */
    public void beginFrame() {
        if (!initialised) return;
        if (pipelineWatcher.tick()) {
            refreshIrisAdapter();
        }
        irisAdapter.bindForDraw();
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
