package me.xssmusashi.farsight.render;

import me.xssmusashi.farsight.FarsightClient;
import me.xssmusashi.farsight.core.mesh.MeshFormat;
import me.xssmusashi.farsight.render.shader.IrisAdapter;
import me.xssmusashi.farsight.render.shader.IrisCompatibility;
import me.xssmusashi.farsight.render.shader.PipelineWatcher;
import me.xssmusashi.farsight.render.shader.ShaderOverrides;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL46;

import java.nio.FloatBuffer;

/**
 * Top-level renderer orchestrator. Lazily initialises GL resources the first
 * time {@link #ensureInitialised()} is called from a thread that owns the GL
 * context; call {@link #close()} from the same thread on shutdown.
 *
 * <p>Phase A: a live draw pass — reads sections that have been registered
 * by {@link SectionLoader}, runs the frustum/Hi-Z culling compute to build
 * an indirect-draw list, then issues a single
 * {@code glMultiDrawElementsIndirectCount} covering every visible section.</p>
 */
public final class FarsightRenderer implements AutoCloseable {
    private static final int DEFAULT_MAX_SECTIONS = 16_384;
    private static final long DEFAULT_VBO_BYTES = 256L * 1024L * 1024L;

    private GpuContext context;
    private ShaderProgram sectionProgram;
    private CullingCompute culling;
    private SectionVboPool vboPool;
    private SectionRegistry registry;
    private SectionLoader loader;
    private MdicDrawBuffer drawBuffer;
    private QuadIndexBuffer indexBuffer;
    private int vao;
    private IrisAdapter irisAdapter;
    private PipelineWatcher pipelineWatcher;
    private boolean initialised;

    public void ensureInitialised() {
        if (initialised) return;
        try {
            context = GpuContext.probe();
            pipelineWatcher = PipelineWatcher.defaultInstance();
            irisAdapter = IrisAdapter.resolve();
            sectionProgram = compileSectionProgram(irisAdapter);
            culling = new CullingCompute();
            vboPool = new SectionVboPool(DEFAULT_VBO_BYTES);
            int maxSections = Math.min(DEFAULT_MAX_SECTIONS,
                Math.max(1024, FarsightClient.CONFIG.lodRenderDistance * 256));
            registry = new SectionRegistry(maxSections);
            loader = new SectionLoader(vboPool, registry);
            drawBuffer = new MdicDrawBuffer(maxSections);
            indexBuffer = new QuadIndexBuffer(QuadIndexBuffer.DEFAULT_MAX_QUADS);
            vao = createVao(indexBuffer.id());
            initialised = true;
            FarsightClient.LOGGER.info(
                "Farsight renderer initialised (iris={}, pack={}, maxSections={})",
                IrisCompatibility.get().isInstalled(),
                IrisCompatibility.get().activeShaderPackName(),
                maxSections);
        } catch (RuntimeException e) {
            FarsightClient.LOGGER.error("renderer init failed", e);
            close();
            throw e;
        }
    }

    private static int createVao(int elementBufferId) {
        int vao = GL46.glGenVertexArrays();
        GL46.glBindVertexArray(vao);
        GL46.glBindBuffer(GL46.GL_ELEMENT_ARRAY_BUFFER, elementBufferId);
        GL46.glBindVertexArray(0);
        return vao;
    }

    private static ShaderProgram compileSectionProgram(IrisAdapter adapter) {
        java.nio.file.Path packRoot = adapter == null ? null : adapter.shaderPackRoot();
        String vert = ShaderOverrides.load(packRoot, ShaderOverrides.VERT_FILE,
            "/assets/farsight/shaders/section.vert");
        String frag = ShaderOverrides.load(packRoot, ShaderOverrides.FRAG_FILE,
            "/assets/farsight/shaders/section.frag");
        return ShaderProgram.graphics(vert, frag);
    }

    public void refreshIrisAdapter() {
        this.irisAdapter = IrisAdapter.resolve();
        FarsightClient.LOGGER.info("Iris adapter refreshed → active={}, packRoot={}",
            irisAdapter.isActive(), irisAdapter.shaderPackRoot());
        try {
            ShaderProgram next = compileSectionProgram(irisAdapter);
            if (sectionProgram != null) sectionProgram.close();
            sectionProgram = next;
        } catch (RuntimeException e) {
            FarsightClient.LOGGER.error("section program recompile on hot-swap failed — keeping old program", e);
        }
    }

    /** Called every frame from {@link FarsightRenderHook#onFrame}. */
    public void beginFrame() {
        if (!initialised) return;
        if (pipelineWatcher.tick()) {
            refreshIrisAdapter();
        }
        irisAdapter.bindForDraw();
        loader.tick();
    }

    /**
     * Full draw pass: frustum/Hi-Z culling compute → indirect draw. Safe to
     * call before sections have been loaded (just issues an empty draw).
     */
    public void drawFrame(Matrix4f viewProjection) {
        if (!initialised) return;
        int sectionCount = registry.highWaterMark();
        if (sectionCount == 0) return;

        // Dispatch culling compute — populates drawBuffer commands + counter.
        FloatBuffer mat = org.lwjgl.BufferUtils.createFloatBuffer(16);
        viewProjection.get(mat);
        float[] matArray = new float[16];
        mat.get(matArray);
        culling.dispatch(
            sectionCount,
            registry.ssboId(),
            drawBuffer.commandBufferId(),
            drawBuffer.counterBufferId(),
            /* hiZTex     */ 0,
            /* hiZWidth   */ 1,
            /* hiZHeight  */ 1,
            matArray);

        // Bind common resources for the draw.
        GL46.glBindVertexArray(vao);
        GL46.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, vboPool.bufferId());
        GL46.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, registry.ssboId());
        GL46.glBindBuffer(GL46.GL_DRAW_INDIRECT_BUFFER, drawBuffer.commandBufferId());
        GL46.glBindBuffer(GL46.GL_PARAMETER_BUFFER,     drawBuffer.counterBufferId());

        sectionProgram.use();
        GL46.glUniformMatrix4fv(
            sectionProgram.uniformLocation("u_viewProj"), false, matArray);

        GL46.glMultiDrawElementsIndirectCount(
            GL46.GL_TRIANGLES,
            GL46.GL_UNSIGNED_INT,
            0L,
            0L,
            drawBuffer.maxCommands(),
            0);

        GL46.glBindBuffer(GL46.GL_DRAW_INDIRECT_BUFFER, 0);
        GL46.glBindBuffer(GL46.GL_PARAMETER_BUFFER, 0);
        GL46.glBindVertexArray(0);
    }

    public boolean isInitialised()          { return initialised; }
    public GpuContext context()              { return context; }
    public IrisAdapter irisAdapter()         { return irisAdapter; }
    public PipelineWatcher pipelineWatcher() { return pipelineWatcher; }
    public SectionRegistry registry()        { return registry; }

    @Override
    public void close() {
        try { if (vao != 0) GL46.glDeleteVertexArrays(vao); } catch (Exception ignored) {}
        try { if (indexBuffer != null) indexBuffer.close(); } catch (Exception ignored) {}
        try { if (culling != null) culling.close(); } catch (Exception ignored) {}
        try { if (drawBuffer != null) drawBuffer.close(); } catch (Exception ignored) {}
        try { if (registry != null) registry.close(); } catch (Exception ignored) {}
        try { if (vboPool != null) vboPool.close(); } catch (Exception ignored) {}
        try { if (sectionProgram != null) sectionProgram.close(); } catch (Exception ignored) {}
        initialised = false;
    }
}
