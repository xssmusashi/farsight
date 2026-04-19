package me.xssmusashi.farsight.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import me.xssmusashi.farsight.FarsightClient;
import me.xssmusashi.farsight.render.FarsightFrameState;
import me.xssmusashi.farsight.render.FarsightRenderHook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    private static boolean farsight$dumpedCameraState = false;

    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void farsight$onRenderLevel(
            GraphicsResourceAllocator allocator,
            DeltaTracker tickCounter,
            boolean renderBlockOutline,
            CameraRenderState cameraRenderState,
            Matrix4fc projectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            ChunkSectionsToRender chunkSectionsToRender,
            CallbackInfo ci) {

        Matrix4f viewProj = farsight$buildViewProj(cameraRenderState, projectionMatrix);
        FarsightFrameState.update(viewProj);

        if (!farsight$dumpedCameraState) {
            farsight$dumpedCameraState = true;
            farsight$dumpCameraRenderState(cameraRenderState);
            FarsightClient.LOGGER.info("renderLevel arg projection m00={} m11={} m22={} m33={} m23={} m32={}",
                projectionMatrix.m00(), projectionMatrix.m11(), projectionMatrix.m22(),
                projectionMatrix.m33(), projectionMatrix.m23(), projectionMatrix.m32());
            FarsightClient.LOGGER.info("composed viewProj m00={} m11={} m22={} m33={} m03={} m13={} m23={}",
                viewProj.m00(), viewProj.m11(), viewProj.m22(), viewProj.m33(),
                viewProj.m03(), viewProj.m13(), viewProj.m23());
        }

        FarsightRenderHook.onFrame();
    }

    /**
     * Builds {@code projection × viewRotation × translate(-cameraPos)} — the
     * transform MC applies around every {@code renderLevel} call. Both
     * {@code viewRotationMatrix} and {@code pos} are fields on
     * {@link CameraRenderState} (see {@code farsight$dumpCameraRenderState}
     * output logged on the first frame). Reflection is used so Farsight does
     * not need a compile-time dependency on internal MC class shape.
     */
    private static Matrix4f farsight$buildViewProj(CameraRenderState state, Matrix4fc projection) {
        Matrix4f result = new Matrix4f(projection);
        if (state == null) return result;
        try {
            Class<?> c = state.getClass();

            Field viewRotField = c.getDeclaredField("viewRotationMatrix");
            viewRotField.setAccessible(true);
            Object rot = viewRotField.get(state);
            if (rot instanceof Matrix4fc m) {
                result.mul(m);
            }

            Field posField = c.getDeclaredField("pos");
            posField.setAccessible(true);
            Object pos = posField.get(state);
            if (pos != null) {
                Class<?> pc = pos.getClass();
                double px = pc.getField("x").getDouble(pos);
                double py = pc.getField("y").getDouble(pos);
                double pz = pc.getField("z").getDouble(pos);
                result.translate((float) -px, (float) -py, (float) -pz);
            }
        } catch (Throwable t) {
            FarsightClient.LOGGER.debug("view-proj build fallback: {}", t.toString());
        }
        return result;
    }

    private static void farsight$dumpCameraRenderState(CameraRenderState state) {
        try {
            FarsightClient.LOGGER.info("=== Farsight CameraRenderState dump ===");
            if (state == null) {
                FarsightClient.LOGGER.info("cameraRenderState is null");
                return;
            }
            Class<?> c = state.getClass();
            FarsightClient.LOGGER.info("class: {}", c.getName());
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(state);
                    String shown;
                    if (v == null) {
                        shown = "null";
                    } else if (v instanceof Matrix4fc m) {
                        shown = "Matrix4f[00=" + m.m00() + " 03=" + m.m03() + " 30=" + m.m30() + " 33=" + m.m33() + "]";
                    } else {
                        shown = String.valueOf(v);
                        if (shown.length() > 200) shown = shown.substring(0, 200) + "…";
                    }
                    FarsightClient.LOGGER.info("  {} {} = {}", f.getType().getSimpleName(), f.getName(), shown);
                } catch (Throwable t) {
                    FarsightClient.LOGGER.info("  {} {} = <{}>", f.getType().getSimpleName(), f.getName(), t);
                }
            }
            for (var m : c.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && !m.getReturnType().equals(void.class)) {
                    FarsightClient.LOGGER.info("  method: {} {}()", m.getReturnType().getSimpleName(), m.getName());
                }
            }
            FarsightClient.LOGGER.info("=== end Farsight dump ===");
        } catch (Throwable t) {
            FarsightClient.LOGGER.warn("camera dump failed", t);
        }
    }
}
