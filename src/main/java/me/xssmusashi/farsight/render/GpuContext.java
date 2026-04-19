package me.xssmusashi.farsight.render;

import me.xssmusashi.farsight.FarsightClient;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL46;
import org.lwjgl.opengl.GLCapabilities;

/**
 * Snapshot of the OpenGL capabilities Farsight depends on. Constructed once
 * per session from whatever context Minecraft is running in.
 */
public final class GpuContext {
    public final int versionMajor;
    public final int versionMinor;
    public final boolean hasBindlessTextures;
    public final boolean hasMultiDrawIndirect;
    public final boolean hasComputeShaders;
    public final boolean hasPersistentMappedBuffers;
    public final boolean isFullySupported;

    private GpuContext(int major, int minor, boolean bindless, boolean mdi,
                       boolean compute, boolean persistentMap) {
        this.versionMajor = major;
        this.versionMinor = minor;
        this.hasBindlessTextures = bindless;
        this.hasMultiDrawIndirect = mdi;
        this.hasComputeShaders = compute;
        this.hasPersistentMappedBuffers = persistentMap;
        this.isFullySupported =
            major >= 4 && minor >= 6 && bindless && mdi && compute && persistentMap;
    }

    public static GpuContext probe() {
        GLCapabilities caps = GL.getCapabilities();
        int major = GL46.glGetInteger(GL46.GL_MAJOR_VERSION);
        int minor = GL46.glGetInteger(GL46.GL_MINOR_VERSION);
        GpuContext ctx = new GpuContext(
            major, minor,
            caps.GL_ARB_bindless_texture,
            caps.GL_ARB_multi_draw_indirect || (major >= 4 && minor >= 3),
            caps.OpenGL43,
            caps.GL_ARB_buffer_storage || (major >= 4 && minor >= 4)
        );
        FarsightClient.LOGGER.info(
            "GPU context: GL {}.{} bindless={} mdi={} compute={} persistentMap={}",
            major, minor,
            ctx.hasBindlessTextures, ctx.hasMultiDrawIndirect,
            ctx.hasComputeShaders, ctx.hasPersistentMappedBuffers);
        if (!ctx.isFullySupported) {
            FarsightClient.LOGGER.warn(
                "Farsight needs full OpenGL 4.6 support — falling back to CPU culling where needed");
        }
        return ctx;
    }
}
