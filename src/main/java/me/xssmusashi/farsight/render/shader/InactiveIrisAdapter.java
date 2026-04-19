package me.xssmusashi.farsight.render.shader;

/** No-op {@link IrisAdapter} used when Iris is missing or no pack is active. */
public final class InactiveIrisAdapter implements IrisAdapter {
    @Override public boolean isActive()         { return false; }
    @Override public int gbufferFboId()         { return 0; }
    @Override public int colorAttachmentCount() { return 1; }
    @Override public int terrainProgramId()     { return 0; }
}
