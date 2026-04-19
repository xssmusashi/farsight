package me.xssmusashi.farsight.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import me.xssmusashi.farsight.render.FarsightFrameState;
import me.xssmusashi.farsight.render.FarsightRenderHook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks MC's per-frame world render entry point. MC 26.1.1 frame-graph
 * signature is
 * {@code renderLevel(GraphicsResourceAllocator, DeltaTracker, boolean,
 * CameraRenderState, Matrix4fc, GpuBufferSlice, Vector4f, boolean,
 * ChunkSectionsToRender)} — only the projection matrix is passed; view
 * transforms live on {@code CameraRenderState}.
 *
 * <p>{@code require = 0} keeps mod load resilient against 26.1.x drift —
 * ingest + LMDB keep working even if the render path sits idle.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

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
        FarsightFrameState.update(projectionMatrix);
        FarsightRenderHook.onFrame();
    }
}
