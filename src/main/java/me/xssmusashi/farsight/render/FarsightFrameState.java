package me.xssmusashi.farsight.render;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Single-producer/single-consumer holder for the current frame's projection
 * matrix. MC 26.1.1's frame-graph {@code LevelRenderer.renderLevel} passes a
 * single {@code Matrix4fc} (no separate position matrix) — we cache it here
 * for the renderer's {@code u_viewProj} uniform.
 */
public final class FarsightFrameState {
    public record Frame(Matrix4f matrix) {}

    private static final AtomicReference<Frame> CURRENT = new AtomicReference<>();

    private FarsightFrameState() {}

    public static void update(Matrix4fc matrix) {
        CURRENT.set(new Frame(new Matrix4f(matrix)));
    }

    public static Frame current() {
        return CURRENT.get();
    }

    public static Matrix4f viewProjection() {
        Frame f = CURRENT.get();
        if (f == null) return null;
        return new Matrix4f(f.matrix);
    }
}
