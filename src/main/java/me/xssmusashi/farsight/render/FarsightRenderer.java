package me.xssmusashi.farsight.render;

import me.xssmusashi.farsight.FarsightClient;
import me.xssmusashi.farsight.render.shader.IrisAdapter;
import me.xssmusashi.farsight.render.shader.IrisCompatibility;
import me.xssmusashi.farsight.render.shader.PipelineWatcher;
import me.xssmusashi.farsight.render.shader.ShaderOverrides;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL46;

/**
 * GL 3.3-core renderer. MC exposes a 3.3 core context even on drivers that
 * support 4.6, so the MDI + compute path from the earlier iteration is
 * unreachable — calling {@code glMultiDrawElementsIndirectCount} crashes
 * the JVM natively. This class therefore issues one
 * {@code glDrawElementsBaseVertex} per registered section with
 * {@code u_sectionOrigin} / {@code u_voxelScale} set as uniforms; a VAO
 * binds the mega VBO once and the shared quad-index buffer rides alongside.
 */
public final class FarsightRenderer implements AutoCloseable {
    private static final int DEFAULT_MAX_SECTIONS = 8_192;
    private static final long DEFAULT_VBO_BYTES = 256L * 1024L * 1024L;

    private GpuContext context;
    private ShaderProgram sectionProgram;
    private SectionVboPool vboPool;
    private SectionRegistry registry;
    private SectionLoader loader;
    private QuadIndexBuffer indexBuffer;
    private int vao;
    private int uViewProj = -1;
    private int uSectionOrigin = -1;
    private int uVoxelScale = -1;
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
            uViewProj = sectionProgram.uniformLocation("u_viewProj");
            uSectionOrigin = sectionProgram.uniformLocation("u_sectionOrigin");
            uVoxelScale = sectionProgram.uniformLocation("u_voxelScale");
            vboPool = new SectionVboPool(DEFAULT_VBO_BYTES);
            int maxSections = Math.min(DEFAULT_MAX_SECTIONS,
                Math.max(1024, FarsightClient.CONFIG.lodRenderDistance * 256));
            registry = new SectionRegistry(maxSections);
            loader = new SectionLoader(vboPool, registry);
            indexBuffer = new QuadIndexBuffer(QuadIndexBuffer.DEFAULT_MAX_QUADS);
            vao = createVao(indexBuffer.id(), vboPool.bufferId());
            initialised = true;
            FarsightClient.LOGGER.info(
                "Farsight renderer initialised — CPU-fallback draw loop, iris={}, pack={}, maxSections={}",
                IrisCompatibility.get().isInstalled(),
                IrisCompatibility.get().activeShaderPackName(),
                maxSections);
        } catch (RuntimeException e) {
            FarsightClient.LOGGER.error("renderer init failed", e);
            close();
            throw e;
        }
    }

    private static int createVao(int elementBufferId, int vertexBufferId) {
        int vao = GL33.glGenVertexArrays();
        GL33.glBindVertexArray(vao);
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vertexBufferId);
        GL33.glEnableVertexAttribArray(0);
        GL33.glVertexAttribIPointer(0, 4, GL33.GL_UNSIGNED_INT, 16, 0L);
        GL33.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, elementBufferId);
        GL33.glBindVertexArray(0);
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, 0);
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
            uViewProj = sectionProgram.uniformLocation("u_viewProj");
            uSectionOrigin = sectionProgram.uniformLocation("u_sectionOrigin");
            uVoxelScale = sectionProgram.uniformLocation("u_voxelScale");
        } catch (RuntimeException e) {
            FarsightClient.LOGGER.error("section program recompile on hot-swap failed — keeping old program", e);
        }
    }

    public void beginFrame() {
        if (!initialised) return;
        if (pipelineWatcher.tick()) {
            refreshIrisAdapter();
        }
        irisAdapter.bindForDraw();
        loader.tick();
    }

    /**
     * Iterates the live section registry and issues one glDrawElementsBaseVertex
     * per section. Ugly but GL-3.3-safe.
     */
    public void drawFrame(Matrix4f viewProjection) {
        if (!initialised) return;
        if (registry.liveCount() == 0) return;

        float[] matArray = new float[16];
        viewProjection.get(matArray);

        sectionProgram.use();
        GL33.glUniformMatrix4fv(uViewProj, false, matArray);

        GL33.glBindVertexArray(vao);

        SectionRegistry.Slot[] snap = registry.snapshot();
        for (int i = 0; i < snap.length; i++) {
            SectionRegistry.Slot s = snap[i];
            if (s == null) continue;
            GL33.glUniform3f(uSectionOrigin, s.originX(), s.originY(), s.originZ());
            GL33.glUniform1f(uVoxelScale, s.voxelScale());
            GL33.glDrawElementsBaseVertex(
                GL33.GL_TRIANGLES,
                s.indexCount(),
                GL33.GL_UNSIGNED_INT,
                0L,
                s.baseVertex());
        }

        GL33.glBindVertexArray(0);
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
        try { if (registry != null) registry.close(); } catch (Exception ignored) {}
        try { if (vboPool != null) vboPool.close(); } catch (Exception ignored) {}
        try { if (sectionProgram != null) sectionProgram.close(); } catch (Exception ignored) {}
        initialised = false;
    }
}
