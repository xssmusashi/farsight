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
        // Temporarily skip Iris FBO binding while debugging geometry visibility —
        // draw into whatever MC/Sodium has bound so we share the main backbuffer.
        // irisAdapter.bindForDraw();
        loader.tick();
    }

    private long frameCounter = 0L;

    /**
     * Iterates the live section registry and issues one glDrawElementsBaseVertex
     * per section. Ugly but GL-3.3-safe.
     */
    public void drawFrame(Matrix4f viewProjection) {
        if (!initialised) return;
        int live = registry.liveCount();
        frameCounter++;

        if ((frameCounter % 300L) == 0L) {
            float[] m = new float[16];
            viewProjection.get(m);
            FarsightClient.LOGGER.info(
                "Farsight draw f={} live={}: viewProj row0=[{},{},{},{}] row3=[{},{},{},{}]",
                frameCounter, live,
                m[0], m[4], m[8], m[12],
                m[3], m[7], m[11], m[15]);
            // Sample first registered slot's origin to confirm CPU state
            SectionRegistry.Slot[] snap2 = registry.snapshot();
            for (SectionRegistry.Slot s : snap2) {
                if (s != null) {
                    FarsightClient.LOGGER.info(
                        "  sample slot: origin=({},{},{}) scale={} baseVertex={} indexCount={}",
                        s.originX(), s.originY(), s.originZ(), s.voxelScale(),
                        s.baseVertex(), s.indexCount());
                    break;
                }
            }
        }

        if (live == 0) return;

        float[] matArray = new float[16];
        viewProjection.get(matArray);

        // DEBUG: force geometry on top of everything, both sides visible.
        // Leave MC's framebuffer bound — binding 0 wipes the already-composited world.
        GL33.glDisable(GL33.GL_DEPTH_TEST);
        GL33.glDisable(GL33.GL_CULL_FACE);
        GL33.glDisable(GL33.GL_BLEND);
        GL33.glDisable(GL33.GL_STENCIL_TEST);
        GL33.glDepthMask(true);
        GL33.glColorMask(true, true, true, true);

        // CANARY: log actual viewport then paint a giant red rectangle.
        // If user sees full-screen red we're writing into the display buffer.
        if ((frameCounter % 300L) == 2L) {
            int[] vp = new int[4];
            GL33.glGetIntegerv(GL33.GL_VIEWPORT, vp);
            int drawFbo = GL33.glGetInteger(GL33.GL_DRAW_FRAMEBUFFER_BINDING);
            FarsightClient.LOGGER.info("render-slot viewport=[{},{},{},{}] drawFbo={}",
                vp[0], vp[1], vp[2], vp[3], drawFbo);
        }
        // DEBUG: MC 26.1.1's blaze3d refactor moved rendering behind a
        // GpuDevice abstraction — direct glClear/glDrawElements do not
        // reach the displayed framebuffer. Raw-GL render path is parked
        // pending a proper blaze3d integration.

        // Clip-space sanity check: see how many registered slots actually
        // fall inside the frustum with the current matrix. If zero, either
        // the matrix is still wrong or everything is out of range.
        if ((frameCounter % 300L) == 1L) {
            SectionRegistry.Slot[] probe = registry.snapshot();
            int inFrustum = 0;
            int testedMax = Math.min(probe.length, 500);
            for (int i = 0; i < testedMax; i++) {
                SectionRegistry.Slot s = probe[i];
                if (s == null) continue;
                float cx = s.originX() + s.extent() * 0.5f;
                float cy = s.originY() + s.extent() * 0.5f;
                float cz = s.originZ() + s.extent() * 0.5f;
                org.joml.Vector4f v = new org.joml.Vector4f(cx, cy, cz, 1f);
                viewProjection.transform(v);
                if (v.w > 0 && Math.abs(v.x) < v.w * 1.5f && Math.abs(v.y) < v.w * 1.5f) {
                    inFrustum++;
                }
            }
            FarsightClient.LOGGER.info("clip-space sanity: {}/{} tested slot centres inside frustum",
                inFrustum, testedMax);
        }

        if ((frameCounter % 300L) == 0L) {
            int[] vp = new int[4];
            GL33.glGetIntegerv(GL33.GL_VIEWPORT, vp);
            int currentFbo = GL33.glGetInteger(GL33.GL_DRAW_FRAMEBUFFER_BINDING);
            int currentProgram = GL33.glGetInteger(GL33.GL_CURRENT_PROGRAM);
            FarsightClient.LOGGER.info("GL state before draw: viewport=[{},{},{},{}] drawFbo={} prevProgram={}",
                vp[0], vp[1], vp[2], vp[3], currentFbo, currentProgram);
        }

        sectionProgram.use();
        GL33.glUniformMatrix4fv(uViewProj, false, matArray);

        GL33.glBindVertexArray(vao);

        SectionRegistry.Slot[] snap = registry.snapshot();
        int drawCalls = 0;
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
            drawCalls++;
        }

        GL33.glBindVertexArray(0);
        GL33.glUseProgram(0);

        // Restore defaults MC may have had before our draw
        GL33.glEnable(GL33.GL_DEPTH_TEST);
        GL33.glEnable(GL33.GL_CULL_FACE);

        int err = GL33.glGetError();
        if (err != GL33.GL_NO_ERROR) {
            if ((frameCounter % 60L) == 0L) {
                FarsightClient.LOGGER.warn("GL error after draw frame: 0x{} (draws={})",
                    Integer.toHexString(err), drawCalls);
            }
        }
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
