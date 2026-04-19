package me.xssmusashi.farsight.core.mesh;

/**
 * GPU vertex layout for Farsight section meshes.
 *
 * <p>16 bytes per vertex, tightly packed to fit bindless SSBO accesses:</p>
 * <pre>
 *   u32 packedPosNormal:
 *       bits  0..7  — x (0..255 within an extended section)
 *       bits  8..15 — y
 *       bits 16..23 — z
 *       bits 24..26 — face index (0..5: -X,+X,-Y,+Y,-Z,+Z)
 *       bits 27..31 — reserved
 *   u32 packedAoUv:
 *       bits  0..7  — ambient-occlusion (0..255)
 *       bits  8..17 — u texcoord (fixed-point, 10-bit)
 *       bits 18..27 — v texcoord
 *       bits 28..31 — reserved
 *   u32 stateId:
 *       packed blockstate id (24 bits) + flags (8 bits)
 *   u32 reserved:
 *       kept for future per-vertex data (biome, emissive, subpixel offset)
 * </pre>
 * <p>Four vertices per quad; two triangles via an index buffer of 6 ushort.</p>
 */
public final class MeshFormat {
    public static final int VERTEX_BYTES = 16;
    public static final int VERTICES_PER_QUAD = 4;
    public static final int INDICES_PER_QUAD = 6;
    public static final int QUAD_BYTES = VERTICES_PER_QUAD * VERTEX_BYTES;

    public static final int FACE_NEG_X = 0;
    public static final int FACE_POS_X = 1;
    public static final int FACE_NEG_Y = 2;
    public static final int FACE_POS_Y = 3;
    public static final int FACE_NEG_Z = 4;
    public static final int FACE_POS_Z = 5;

    private MeshFormat() {}

    public static int faceIndex(int axis, int side) {
        return (axis << 1) | (side > 0 ? 1 : 0);
    }

    public static int packPosNormal(int x, int y, int z, int faceIndex) {
        return (x & 0xFF)
             | ((y & 0xFF) << 8)
             | ((z & 0xFF) << 16)
             | ((faceIndex & 0x7) << 24);
    }

    public static int packAoUv(int ao, int uFixed, int vFixed) {
        return (ao & 0xFF)
             | ((uFixed & 0x3FF) << 8)
             | ((vFixed & 0x3FF) << 18);
    }

    public static int packStateFlags(int blockstate, int flags) {
        return (blockstate & 0xFFFFFF) | ((flags & 0xFF) << 24);
    }

    /**
     * Builds a shared quad→triangle index buffer of capacity
     * {@code maxQuads} quads. Pattern per quad is
     * {@code [v0,v1,v2, v0,v2,v3]} where {@code v0..v3} are the four
     * consecutive vertex indices within the section. Same IBO is reused by
     * every section — {@code baseVertex} on the indirect-draw command
     * shifts the reads into that section's block of the mega VBO.
     */
    public static int[] buildQuadIndexPattern(int maxQuads) {
        int[] idx = new int[maxQuads * INDICES_PER_QUAD];
        for (int q = 0; q < maxQuads; q++) {
            int v0 = q * VERTICES_PER_QUAD;
            int v1 = v0 + 1;
            int v2 = v0 + 2;
            int v3 = v0 + 3;
            int o = q * INDICES_PER_QUAD;
            idx[o]     = v0;
            idx[o + 1] = v1;
            idx[o + 2] = v2;
            idx[o + 3] = v0;
            idx[o + 4] = v2;
            idx[o + 5] = v3;
        }
        return idx;
    }
}
