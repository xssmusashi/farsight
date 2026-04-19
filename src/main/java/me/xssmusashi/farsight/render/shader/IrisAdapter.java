package me.xssmusashi.farsight.render.shader;

import org.lwjgl.opengl.GL46;

/**
 * Abstraction over the Iris gbuffer so the renderer can target either its
 * own framebuffer (no shader pack active) or Iris's bound gbuffer attachments
 * (shader pack active).
 *
 * <p>Two concrete implementations:</p>
 * <ul>
 *   <li>{@link Inactive} — used when Iris is not installed or no pack is
 *       active. Farsight renders into the default backbuffer.</li>
 *   <li>{@link IrisBacked} — reflective adapter that queries Iris's current
 *       gbuffer framebuffer and program handles. Currently returns read-only
 *       stubs; a future session will wire the actual FBO bind + MRT writes.</li>
 * </ul>
 *
 * <p>The separation here keeps Farsight's core render path free of {@code
 * net.irisshaders} imports. {@link IrisBacked} does all reflection in one
 * place and all other call sites go through this interface.</p>
 */
public sealed interface IrisAdapter permits IrisAdapter.Inactive, IrisAdapter.IrisBacked {

    boolean isActive();

    /** Iris's current gbuffer FBO id, or 0 if none. */
    int gbufferFboId();

    /** Number of colour attachments the current shader pack binds (usually 4-8). */
    int colorAttachmentCount();

    /**
     * Returns a program handle that Iris would use to render terrain for
     * the current pass, or 0 if Farsight should use its own shaders.
     * Reserved for the future Phase-7-full implementation.
     */
    int terrainProgramId();

    /** Binds the gbuffer before Farsight draws. */
    default void bindForDraw() {
        int fbo = gbufferFboId();
        if (fbo != 0) {
            GL46.glBindFramebuffer(GL46.GL_DRAW_FRAMEBUFFER, fbo);
        }
    }

    static IrisAdapter resolve() {
        return IrisCompatibility.get().isShaderPackActive()
            ? new IrisBacked()
            : new Inactive();
    }

    final class Inactive implements IrisAdapter {
        public boolean isActive()         { return false; }
        public int gbufferFboId()         { return 0; }
        public int colorAttachmentCount() { return 1; }
        public int terrainProgramId()     { return 0; }
    }

    /**
     * Minimal reflective bridge. Resolves nothing right now — returns the
     * same no-op values as {@link Inactive} but advertises that it <em>is</em>
     * active. Lets the rest of the renderer exercise the
     * "shader-pack-detected" code path while Phase 7 proper is built.
     */
    final class IrisBacked implements IrisAdapter {
        public boolean isActive()         { return true; }
        public int gbufferFboId()         { return 0; }  // TODO: resolve via Iris internals
        public int colorAttachmentCount() { return 4; }  // TODO: read from shader pack config
        public int terrainProgramId()     { return 0; }  // TODO: fetch gbuffers_terrain
    }
}
