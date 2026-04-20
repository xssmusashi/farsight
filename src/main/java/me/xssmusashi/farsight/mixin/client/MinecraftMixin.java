package me.xssmusashi.farsight.mixin.client;

import me.xssmusashi.farsight.render.FarsightRenderHook;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws Farsight at the end of {@link Minecraft#renderFrame}. This fires after
 * the world frame-graph has executed, after MC has composited into the
 * backbuffer, and after HUD layers have been drawn — but before the window
 * buffer swap. Everything we draw here lands on-screen for the current frame.
 *
 * <p>Camera/projection data was captured earlier in
 * {@link LevelRendererMixin} at HEAD of renderLevel, so by the time this
 * mixin fires {@code FarsightFrameState} is already populated for the frame.</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    private static int farsight$hitCount = 0;

    @Inject(method = "renderFrame", at = @At("RETURN"), require = 0)
    private void farsight$afterRenderFrame(CallbackInfo ci) {
        if ((farsight$hitCount++ & 0xFF) == 0) {
            System.out.println("[Farsight-mixin] renderFrame RETURN hit #" + farsight$hitCount);
        }
        FarsightRenderHook.onFrame();
    }
}
