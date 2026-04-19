package me.xssmusashi.farsight.core.mesh;

import me.xssmusashi.farsight.core.voxel.Section;
import me.xssmusashi.farsight.core.voxel.VoxelEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AmbientOcclusionTest {

    private static final long STONE = VoxelEntry.encode(1, 0, 0, VoxelEntry.FLAG_SOLID, 0);

    @Test
    void singleExposedFaceGetsFullLight() {
        long[] voxels = new long[Section.VOLUME];
        voxels[Section.index(5, 5, 5)] = STONE;
        int ao = AmbientOcclusion.perFace(voxels, 1, +1, 6, 5, 5);
        assertEquals(AmbientOcclusion.FULL_LIGHT, ao);
    }

    @Test
    void surroundedFaceIsDarker() {
        long[] voxels = new long[Section.VOLUME];
        // A strip of stone at y=5 fills the whole slab, the face at +Y (y=6) should be occluded
        // by surrounding stone when there is any solid above
        for (int z = 4; z <= 6; z++) {
            for (int x = 4; x <= 6; x++) {
                voxels[Section.index(x, 5, z)] = STONE;  // our face is on y=5 at (5,5,5)
                voxels[Section.index(x, 6, z)] = STONE;  // solid above — occludes neighbours
            }
        }
        voxels[Section.index(5, 6, 5)] = 0L;  // clear only the direct-above voxel
        int ao = AmbientOcclusion.perFace(voxels, 1, +1, 6, 5, 5);
        // 8 neighbours, all solid
        assertEquals(Math.max(0, AmbientOcclusion.FULL_LIGHT - 8 * AmbientOcclusion.PER_NEIGHBOUR_DARKEN), ao);
    }

    @Test
    void partialOcclusionIsBetweenExtremes() {
        long[] voxels = new long[Section.VOLUME];
        voxels[Section.index(5, 5, 5)] = STONE;
        voxels[Section.index(4, 6, 5)] = STONE;
        voxels[Section.index(6, 6, 5)] = STONE;
        int ao = AmbientOcclusion.perFace(voxels, 1, +1, 6, 5, 5);
        int expected = AmbientOcclusion.FULL_LIGHT - 2 * AmbientOcclusion.PER_NEIGHBOUR_DARKEN;
        assertEquals(expected, ao);
    }
}
