package me.xssmusashi.farsight.render.iris;

import com.mojang.blaze3d.opengl.GlProgram;
import me.xssmusashi.farsight.FarsightClient;
import me.xssmusashi.farsight.render.shader.IrisAdapter;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.ShaderRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.pipeline.programs.ShaderMap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Lives in a dedicated sub-package so that its direct Iris imports are only
 * resolved when Iris is actually on the classpath at runtime. Instantiated
 * reflectively by {@link IrisAdapter#resolve()}; any {@link NoClassDefFoundError}
 * caught there means Iris is missing and the inactive fallback wins.
 *
 * <p>State is recomputed on every accessor call — the underlying
 * {@link IrisRenderingPipeline} can be replaced when the user hot-swaps a
 * shader pack, and caching a stale reference leaves Farsight drawing into an
 * FBO that Iris has already deleted.</p>
 */
public final class RealIrisAdapter implements IrisAdapter {

    /** Iris 1.10.x privately stores the per-shader gbuffer on {@code ExtendedShader}. */
    private static final String WRITING_FIELD = "writingToBeforeTranslucent";
    private volatile Field writingField;

    @Override
    public boolean isActive() {
        return currentIrisPipeline() != null;
    }

    @Override
    public int gbufferFboId() {
        GlFramebuffer fb = currentTerrainFramebuffer();
        return fb == null ? 0 : fb.getId();
    }

    @Override
    public int colorAttachmentCount() {
        // The terrain shader's framebuffer carries whatever draw-buffers the
        // shader pack declared. We can't query the count without more
        // reflection than this session is worth; 4 is the common Iris default
        // (albedo/normal/lightmap/meta).
        return isActive() ? 4 : 1;
    }

    @Override
    public int terrainProgramId() {
        IrisRenderingPipeline pipeline = currentIrisPipeline();
        if (pipeline == null) return 0;
        ShaderMap map = pipeline.getShaderMap();
        if (map == null) return 0;
        GlProgram program = map.getShader(ShaderKey.TERRAIN_SOLID);
        return program == null ? 0 : program.getProgramId();
    }

    @Override
    public void bindForDraw() {
        GlFramebuffer fb = currentTerrainFramebuffer();
        if (fb != null) {
            fb.bindAsDrawBuffer();
        }
    }

    @Override
    public void refresh() {
        // Nothing cached — all accessors re-resolve on every call.
    }

    @Override
    public Path shaderPackRoot() {
        try {
            Path packsDir = Iris.getShaderpacksDirectory();
            if (packsDir == null) return null;
            String name = resolvePackName();
            if (name == null || name.isBlank()) return null;
            Path shaders = packsDir.resolve(name).resolve("shaders");
            return Files.isDirectory(shaders) ? shaders : null;
        } catch (Throwable t) {
            FarsightClient.LOGGER.debug("shaderPackRoot resolution threw", t);
            return null;
        }
    }

    /**
     * Iris 1.10.x did not expose the active pack name on its stable
     * {@code api.v0} surface, and internal methods have shuffled across the
     * 1.9→1.10 boundary. We try a small ordered list of known accessors
     * reflectively — the first one that returns a non-blank string wins.
     */
    private static String resolvePackName() {
        // 1) Iris.getIrisConfig().getShaderPackName() → Optional<String>
        try {
            Method getConfig = Iris.class.getMethod("getIrisConfig");
            Object cfg = getConfig.invoke(null);
            if (cfg != null) {
                Method getName = cfg.getClass().getMethod("getShaderPackName");
                Object v = getName.invoke(cfg);
                if (v instanceof Optional<?> opt && opt.isPresent()) {
                    return opt.get().toString();
                }
                if (v instanceof String s) return s;
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        // 2) Iris.getCurrentPackName() — some versions have this as static
        try {
            Method m = Iris.class.getMethod("getCurrentPackName");
            Object v = m.invoke(null);
            if (v instanceof String s) return s;
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        return null;
    }

    private static IrisRenderingPipeline currentIrisPipeline() {
        try {
            PipelineManager manager = Iris.getPipelineManager();
            if (manager == null) return null;
            WorldRenderingPipeline pipeline = manager.getPipelineNullable();
            return pipeline instanceof IrisRenderingPipeline irp ? irp : null;
        } catch (Throwable t) {
            FarsightClient.LOGGER.debug("Iris pipeline lookup threw", t);
            return null;
        }
    }

    private GlFramebuffer currentTerrainFramebuffer() {
        IrisRenderingPipeline pipeline = currentIrisPipeline();
        if (pipeline == null) return null;
        ShaderRenderingPipeline srp = pipeline;
        ShaderMap map = srp.getShaderMap();
        if (map == null) return null;
        GlProgram program = map.getShader(ShaderKey.TERRAIN_SOLID);
        if (!(program instanceof ExtendedShader ext)) return null;
        try {
            Field f = writingField;
            if (f == null) {
                f = ExtendedShader.class.getDeclaredField(WRITING_FIELD);
                f.setAccessible(true);
                writingField = f;
            }
            Object value = f.get(ext);
            return value instanceof GlFramebuffer gf ? gf : null;
        } catch (NoSuchFieldException e) {
            FarsightClient.LOGGER.debug("ExtendedShader.{} not found — Iris layout drift?", WRITING_FIELD);
            return null;
        } catch (IllegalAccessException e) {
            FarsightClient.LOGGER.debug("cannot read ExtendedShader.{}", WRITING_FIELD, e);
            return null;
        }
    }
}
