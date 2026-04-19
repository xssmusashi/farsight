package me.xssmusashi.farsight.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import me.xssmusashi.farsight.render.FarsightFrameState;
import me.xssmusashi.farsight.render.FarsightRenderHook;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks MC's per-frame world render entry point. Injected at {@code HEAD}
 * of the public {@code renderLevel(...)} method so the state it captures is
 * available to {@link FarsightRenderHook}.
 *
 * <p>Uses {@code require = 0} so signature drift in a 26.1.x point release
 * degrades to a logged warning rather than failing mod load — ingest and
 * LMDB persistence keep working even if the render path sits idle.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void farsight$onRenderLevel(
            GraphicsResourceAllocator allocator,
            DeltaTracker tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f basicProjectionMatrix,
            Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            CallbackInfo ci) {
        // Camera position getters shifted in 26.1.1; skip capture for now — fog
        // falls back to origin, fix in a follow-up when the stable accessor is
        // confirmed.
        FarsightFrameState.update(positionMatrix, projectionMatrix, 0f, 0f, 0f);
        FarsightRenderHook.onFrame();
    }
}
