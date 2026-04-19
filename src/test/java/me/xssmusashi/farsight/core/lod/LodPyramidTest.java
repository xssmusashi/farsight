package me.xssmusashi.farsight.core.lod;

import me.xssmusashi.farsight.core.voxel.Section;
import me.xssmusashi.farsight.core.voxel.VoxelEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LodPyramidTest {

    private static final long STONE = VoxelEntry.encode(1, 0, 0, VoxelEntry.FLAG_SOLID, 0);
    private static final long WATER = VoxelEntry.encode(2, 0, 0, VoxelEntry.FLAG_FLUID, 0);
    private static final long GRASS_PLANT = VoxelEntry.encode(3, 0, 0, VoxelEntry.FLAG_PLANT, 0);

    @Test
    void downsample8AllSame() {
        long result = Downsampler.downsample8(STONE, STONE, STONE, STONE, STONE, STONE, STONE, STONE);
        assertEquals(STONE, result);
    }

    @Test
    void downsample8MajorityWins() {
        long result = Downsampler.downsample8(
            STONE, STONE, STONE, STONE, STONE,
            VoxelEntry.AIR, VoxelEntry.AIR, VoxelEntry.AIR);
        assertEquals(STONE, result);
    }

    @Test
    void downsample8TieBrokenByPriority() {
        // 4 stone, 4 air — tie → solid priority wins
        long result = Downsampler.downsample8(
            STONE, STONE, STONE, STONE,
            VoxelEntry.AIR, VoxelEntry.AIR, VoxelEntry.AIR, VoxelEntry.AIR);
        assertEquals(STONE, result);
    }

    @Test
    void downsample8SolidBeatsFluidOnTie() {
        long result = Downsampler.downsample8(
            STONE, STONE, STONE, STONE,
            WATER, WATER, WATER, WATER);
        assertEquals(STONE, result);
    }

    @Test
    void downsample8FluidBeatsPlantOnTie() {
        long result = Downsampler.downsample8(
            WATER, WATER, WATER, WATER,
            GRASS_PLANT, GRASS_PLANT, GRASS_PLANT, GRASS_PLANT);
        assertEquals(WATER, result);
    }

    @Test
    void pyramidTopOfAllAirIsAir() {
        Section s = new Section();
        LodPyramid p = LodPyramid.build(s);
        assertEquals(VoxelEntry.AIR, p.level(5)[0]);
    }

    @Test
    void pyramidTopOfAllStoneIsStone() {
        Section s = new Section();
        for (int i = 0; i < Section.VOLUME; i++) s.setByIndex(i, STONE);
        LodPyramid p = LodPyramid.build(s);
        assertEquals(STONE, p.level(5)[0]);
    }

    @Test
    void pyramidHasCorrectSizesAtEachLevel() {
        Section s = new Section();
        LodPyramid p = LodPyramid.build(s);
        assertEquals(32 * 32 * 32, p.level(0).length);
        assertEquals(16 * 16 * 16, p.level(1).length);
        assertEquals(8 * 8 * 8,    p.level(2).length);
        assertEquals(4 * 4 * 4,    p.level(3).length);
        assertEquals(2 * 2 * 2,    p.level(4).length);
        assertEquals(1,            p.level(5).length);
    }

    @Test
    void pyramidFlatFloorReducesCorrectly() {
        Section s = new Section();
        // Stone plane y=0, air above
        for (int x = 0; x < Section.SIZE; x++) {
            for (int z = 0; z < Section.SIZE; z++) {
                s.set(x, 0, z, STONE);
            }
        }
        LodPyramid p = LodPyramid.build(s);
        // Level 1 has a "stone" plane at y=0 (16×16) covering the bottom layer.
        long[] l1 = p.level(1);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int i = (0 * 16 + z) * 16 + x; // y=0
                // Each 2³ block at bottom has 4 stone + 4 air → tie → solid wins
                assertEquals(STONE, l1[i]);
            }
        }
    }
}
