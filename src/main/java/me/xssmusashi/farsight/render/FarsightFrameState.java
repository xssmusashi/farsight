package me.xssmusashi.farsight.render;

import org.joml.Matrix4f;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Single-producer/single-consumer holder for the current frame's matrices.
 * The {@code LevelRenderer} mixin writes these on the render thread right
 * before {@link FarsightRenderHook#onFrame} runs; the renderer reads them
 * in the same call. Using an {@link AtomicReference} keeps the publish/read
 * free of torn {@code Matrix4f} structs even if a future threading change
 * splits producer and consumer.
 */
public final class FarsightFrameState {
    public record Frame(Matrix4f positionMatrix, Matrix4f projectionMatrix,
                        float cameraX, float cameraY, float cameraZ) {}

    private static final AtomicReference<Frame> CURRENT = new AtomicReference<>();

    private FarsightFrameState() {}

    public static void update(Matrix4f positionMatrix, Matrix4f projectionMatrix,
                              float cameraX, float cameraY, float cameraZ) {
        CURRENT.set(new Frame(
            new Matrix4f(positionMatrix),
            new Matrix4f(projectionMatrix),
            cameraX, cameraY, cameraZ));
    }

    public static Frame current() {
        return CURRENT.get();
    }

    public static Matrix4f viewProjection() {
        Frame f = CURRENT.get();
        if (f == null) return null;
        return new Matrix4f(f.projectionMatrix).mul(f.positionMatrix);
    }
}
