package me.xssmusashi.farsight.render.shader;

import me.xssmusashi.farsight.FarsightClient;
import org.lwjgl.opengl.GL46;

/**
 * Abstraction over the Iris gbuffer so the renderer can target either its
 * own framebuffer (no shader pack active) or Iris's bound gbuffer attachments
 * (shader pack active).
 *
 * <p>Two concrete implementations:</p>
 * <ul>
 *   <li>{@link InactiveIrisAdapter} — used when Iris is not installed or no
 *       pack is active; renders into the default backbuffer.</li>
 *   <li>{@code me.xssmusashi.farsight.render.iris.RealIrisAdapter} — direct
 *       Iris imports, only loaded when Iris is present at runtime
 *       (instantiated through {@link #resolve()} via {@link Class#forName}
 *       so class resolution is deferred).</li>
 * </ul>
 *
 * <p>All call sites go through this interface — Iris imports are contained to
 * the {@code me.xssmusashi.farsight.render.iris} sub-package.</p>
 */
public interface IrisAdapter {

    boolean isActive();

    /** Iris's current gbuffer FBO id, or 0 if none. */
    int gbufferFboId();

    /** Number of colour attachments the current shader pack binds (usually 4-8). */
    int colorAttachmentCount();

    /** Compiled {@code gbuffers_terrain_solid} program id, or 0 if not available. */
    int terrainProgramId();

    /** Binds the gbuffer before Farsight draws. */
    default void bindForDraw() {
        int fbo = gbufferFboId();
        if (fbo != 0) {
            GL46.glBindFramebuffer(GL46.GL_DRAW_FRAMEBUFFER, fbo);
        }
    }

    /** Re-resolves any cached Iris pipeline references. Call on hot-swap. */
    default void refresh() {}

    /**
     * Factory. Tries to build the real adapter reflectively when Iris is
     * installed and a shader pack is active; falls back to {@link InactiveIrisAdapter}
     * on any failure so Farsight never crashes when Iris is absent or its
     * internal surface has drifted.
     */
    static IrisAdapter resolve() {
        IrisCompatibility compat = IrisCompatibility.get();
        if (!compat.isInstalled() || !compat.isShaderPackActive()) {
            return new InactiveIrisAdapter();
        }
        try {
            Class<?> real = Class.forName("me.xssmusashi.farsight.render.iris.RealIrisAdapter");
            return (IrisAdapter) real.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            FarsightClient.LOGGER.warn(
                "failed to load RealIrisAdapter — falling back to Inactive (Iris internals may have drifted)", t);
            return new InactiveIrisAdapter();
        }
    }
}
