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
import org.joml.Quaternionf;
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
    private void farsight$captureFrameState(
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
        }
        // Actual drawing now happens from HudRenderCallback in FarsightClient —
        // at RETURN of renderLevel the frame graph hasn't blitted to the
        // backbuffer yet, so draws there landed in a discarded intermediate FBO.
        // HudRenderCallback fires AFTER MC's final blit and BEFORE HUD is drawn,
        // which is exactly the compositing slot we want.
    }

    /**
     * Builds view-projection from scratch using:
     *   - hudFov + depthFar + window aspect for JOML's standard perspective (camera looking -Z)
     *   - inverted camera orientation quaternion as the world→view rotation
     *     (safer than the {@code viewRotationMatrix} field whose axis convention
     *     doesn't match JOML's perspective — empirically half of yaw range comes
     *     out with {@code w<0} which clips everything)
     *   - translate(-cameraPos) at the end
     */
    private static Matrix4f farsight$buildViewProj(CameraRenderState state, Matrix4fc projectionArg) {
        if (state == null) return new Matrix4f();
        try {
            Class<?> c = state.getClass();

            float fov = c.getDeclaredField("hudFov").getFloat(state);
            float far = c.getDeclaredField("depthFar").getFloat(state);

            Field orientField = c.getDeclaredField("orientation");
            orientField.setAccessible(true);
            Quaternionf orientation = (Quaternionf) orientField.get(state);

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

            Quaternionf worldToView = new Quaternionf(orientation).invert();

            Matrix4f vp = new Matrix4f();
            vp.perspective((float) Math.toRadians(fov), aspect, 0.05f, Math.max(far, 512f));
            vp.rotate(worldToView);
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
