package me.xssmusashi.farsight.core.mesh;

/**
 * Receiver for quads emitted by a mesher. Decoupled from vertex encoding so
 * that benchmarks can count quads without building a vertex buffer.
 */
public interface MeshOutput {
    /**
     * @param axis       0/1/2 for X/Y/Z (the face-normal axis)
     * @param side       +1 if the normal points in +axis, -1 if in -axis
     * @param planeK     coordinate along {@code axis} where the face lies
     *                   (value in {@code 0..SIZE} inclusive)
     * @param u          low corner along the (axis+1)%3 axis
     * @param v          low corner along the (axis+2)%3 axis
     * @param du         extent along the {@code u} axis, {@code >= 1}
     * @param dv         extent along the {@code v} axis, {@code >= 1}
     * @param blockstate blockstate id of the source voxel (from {@code VoxelEntry})
     */
    void addQuad(int axis, int side, int planeK, int u, int v, int du, int dv, int blockstate);
}
