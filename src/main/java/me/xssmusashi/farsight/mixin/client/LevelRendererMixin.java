package me.xssmusashi.farsight.mixin.client;

import me.xssmusashi.farsight.render.FarsightRenderHook;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks MC's per-frame world render entry point. Injected at {@code HEAD}
 * of the public {@code renderLevel(...)} method — the first thing MC does
 * each frame before any vanilla passes start. Running first is acceptable
 * because the hook currently only performs lazy GL resource init and the
 * Iris pipeline watcher tick; it does not yet touch frame-graph nodes.
 *
 * <p>Uses {@code require = 0} so a signature drift in a 26.1.x point release
 * degrades to a logged warning rather than failing the whole mod load. If
 * the descriptor also drifts, we simply fall back to the "no draw pass"
 * behaviour — Farsight still loads; chunks still persist; only the renderer
 * sits idle until the mixin is updated.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void farsight$onRenderLevel(CallbackInfo ci) {
        FarsightRenderHook.onFrame();
    }
}
