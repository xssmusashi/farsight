package me.xssmusashi.farsight.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import me.xssmusashi.farsight.FarsightClient;
import me.xssmusashi.farsight.render.FarsightFrameState;
import me.xssmusashi.farsight.render.FarsightRenderHook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
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

    @Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
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
     * Builds a view-projection matrix from scratch. The {@code Matrix4fc} arg
     * passed to {@code renderLevel} is actually {@code viewRotationMatrix}
     * (verified by matching {@code m00}), not a perspective matrix — and
     * {@code state.projectionMatrix} field carries {@code m00=Infinity}, so
     * it's unusable too. We therefore rebuild perspective ourselves from
     * {@code hudFov} + {@code depthFar} + window aspect, then apply
     * rotation + camera translation.
     */
    private static Matrix4f farsight$buildViewProj(CameraRenderState state, Matrix4fc projectionArg) {
        if (state == null) return new Matrix4f();
        try {
            Class<?> c = state.getClass();

            float fov = c.getDeclaredField("hudFov").getFloat(state);
            float far = c.getDeclaredField("depthFar").getFloat(state);

            Field viewRotField = c.getDeclaredField("viewRotationMatrix");
            viewRotField.setAccessible(true);
            Matrix4fc viewRot = (Matrix4fc) viewRotField.get(state);

            Field posField = c.getDeclaredField("pos");
            posField.setAccessible(true);
            Object pos = posField.get(state);
            Class<?> pc = pos.getClass();
            double px = pc.getField("x").getDouble(pos);
            double py = pc.getField("y").getDouble(pos);
            double pz = pc.getField("z").getDouble(pos);

            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getWidth();
            int h = mc.getWindow().getHeight();
            float aspect = (h == 0) ? 1f : (float) w / (float) h;

            Matrix4f vp = new Matrix4f();
            vp.perspective((float) Math.toRadians(fov), aspect, 0.05f, Math.max(far, 512f));
            if (viewRot != null) vp.mul(viewRot);
            vp.translate((float) -px, (float) -py, (float) -pz);
            return vp;
        } catch (Throwable t) {
            FarsightClient.LOGGER.debug("buildViewProj fallback: {}", t.toString());
            return new Matrix4f(projectionArg);
        }
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
