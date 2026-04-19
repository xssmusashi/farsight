package me.xssmusashi.farsight.core.lod;

import me.xssmusashi.farsight.core.voxel.Section;
import me.xssmusashi.farsight.core.voxel.VoxelEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrossSectionDownsamplerTest {

    private static final long STONE = VoxelEntry.encode(1, 0, 0, VoxelEntry.FLAG_SOLID, 0);
    private static final long DIRT  = VoxelEntry.encode(2, 0, 0, VoxelEntry.FLAG_SOLID, 0);

    @Test
    void eightAirChildrenProduceAirParent() {
        Section[] kids = new Section[8];
        for (int i = 0; i < 8; i++) kids[i] = new Section();
        Section parent = CrossSectionDownsampler.combine(kids);
        assertTrue(parent.isAllAir());
    }

    @Test
    void nullChildrenTreatedAsAir() {
        Section[] kids = new Section[8];
        Section parent = CrossSectionDownsampler.combine(kids);
        assertTrue(parent.isAllAir());
    }

    @Test
    void eightFullStoneChildrenProduceFullStoneParent() {
        Section[] kids = new Section[8];
        for (int i = 0; i < 8; i++) {
            kids[i] = new Section();
            for (int v = 0; v < Section.VOLUME; v++) kids[i].setByIndex(v, STONE);
        }
        Section parent = CrossSectionDownsampler.combine(kids);
        for (int v = 0; v < Section.VOLUME; v++) {
            assertEquals(STONE, parent.getByIndex(v));
        }
    }

    @Test
    void oneStoneOctantShowsInItsHalf() {
        Section[] kids = new Section[8];
        for (int i = 0; i < 8; i++) kids[i] = new Section();
        Section stoneChild = kids[CrossSectionDownsampler.octantIndex(0, 0, 0)];
        for (int v = 0; v < Section.VOLUME; v++) stoneChild.setByIndex(v, STONE);

        Section parent = CrossSectionDownsampler.combine(kids);
        // The stone octant at (ox=0, oy=0, oz=0) maps to parent voxels
        // PX ∈ [0,15], PY ∈ [0,15], PZ ∈ [0,15].
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    assertEquals(STONE, parent.get(x, y, z),
                        "expected stone at (" + x + "," + y + "," + z + ")");
                }
            }
        }
        // The other octants should be air
        assertEquals(VoxelEntry.AIR, parent.get(17, 5, 5));
    }

    @Test
    void priorityBreaksTieWhenOctantsDiffer() {
        Section[] kids = new Section[8];
        for (int i = 0; i < 8; i++) kids[i] = new Section();
        // fill 4 children with stone, 4 with dirt, in a pattern that forces
        // per-parent-voxel ties at octant seams — downsample uses the solid
        // priority rule consistently so every parent voxel becomes SOLID
        // (either stone or dirt — both have FLAG_SOLID).
        for (int i = 0; i < 4; i++) {
            for (int v = 0; v < Section.VOLUME; v++) kids[i].setByIndex(v, STONE);
        }
        for (int i = 4; i < 8; i++) {
            for (int v = 0; v < Section.VOLUME; v++) kids[i].setByIndex(v, DIRT);
        }
        Section parent = CrossSectionDownsampler.combine(kids);
        for (int v = 0; v < Section.VOLUME; v++) {
            long entry = parent.getByIndex(v);
            assertFalse(VoxelEntry.isAir(entry));
            assertTrue(
                entry == STONE || entry == DIRT,
                "parent voxel " + v + " must be a solid descendant, got " + entry);
        }
    }
}
