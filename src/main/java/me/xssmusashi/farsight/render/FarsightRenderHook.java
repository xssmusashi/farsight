package me.xssmusashi.farsight.render;

import me.xssmusashi.farsight.FarsightClient;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton entry point called by the {@code LevelRenderer} mixin on every
 * rendered frame. Owns the one {@link FarsightRenderer} instance and
 * isolates failures — any exception is logged once and the renderer is
 * permanently disabled for the session so we never enter a
 * {@code log-and-rethrow} loop inside the render thread.
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
            // Draw pass is wired in the next session once we have populated
            // the VBO pool and recorded AABBs for culling. This call keeps
            // Iris hot-swap polling alive and refreshes the pack adapter
            // when packs change.
        } catch (Throwable t) {
            DISABLED.set(true);
            FarsightClient.LOGGER.error("Farsight render hook failed — disabling for this session", t);
        }
    }

    public static void shutdown() {
        try {
            RENDERER.close();
        } catch (Throwable ignored) {}
    }
}
