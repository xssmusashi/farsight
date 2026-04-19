package me.xssmusashi.farsight.core.mesh;

/** Minimal {@link MeshOutput} that only counts emitted quads. */
public final class QuadCounter implements MeshOutput {
    private int count;

    public int count() { return count; }

    public void reset() { count = 0; }

    @Override
    public void addQuad(int axis, int side, int planeK, int u, int v, int du, int dv, int blockstate) {
        count++;
    }
}
