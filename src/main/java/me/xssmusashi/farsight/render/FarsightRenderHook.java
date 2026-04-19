package me.xssmusashi.farsight.render;

import me.xssmusashi.farsight.FarsightClient;
import org.joml.Matrix4f;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton entry point called by the {@code LevelRenderer} mixin every
 * frame. Owns the one {@link FarsightRenderer} instance and isolates render
 * failures — any exception is logged once and the renderer is permanently
 * disabled for the session so we never enter a log-and-rethrow loop on the
 * render thread.
 */
public final class FarsightRenderHook {
    private static final FarsightRenderer RENDERER = new FarsightRenderer();
    private static final AtomicBoolean DISABLED = new AtomicBoolean(false);
    private static final AtomicBoolean INIT_LOGGED = new AtomicBoolean(false);

    private FarsightRenderHook() {}

    public static FarsightRenderer renderer() { return RENDERER; }

    public static void onFrame() {
        if (DISABLED.get()) return;
        if (!FarsightClient.CONFIG.enabled) return;
        try {
            RENDERER.ensureInitialised();
            if (INIT_LOGGED.compareAndSet(false, true)) {
                FarsightClient.LOGGER.info("Farsight render hook first-frame — renderer is live");
            }
            RENDERER.beginFrame();

            Matrix4f viewProj = FarsightFrameState.viewProjection();
            if (viewProj != null) {
                RENDERER.drawFrame(viewProj);
            }
        } catch (Throwable t) {
            DISABLED.set(true);
            FarsightClient.LOGGER.error("Farsight render hook failed — disabling for this session", t);
        }
    }

    public static void shutdown() {
        try { RENDERER.close(); } catch (Throwable ignored) {}
    }
}
