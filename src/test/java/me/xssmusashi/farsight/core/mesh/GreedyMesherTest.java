package me.xssmusashi.farsight.core.mesh;

import me.xssmusashi.farsight.core.voxel.Section;
import me.xssmusashi.farsight.core.voxel.VoxelEntry;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GreedyMesherTest {

    private static final long STONE = VoxelEntry.encode(1, 0, 0, VoxelEntry.FLAG_SOLID, 0);
    private static final long DIRT  = VoxelEntry.encode(2, 0, 0, VoxelEntry.FLAG_SOLID, 0);

    @Test
    void emptySectionYieldsNoQuads() {
        Section s = new Section();
        QuadCounter c = new QuadCounter();
        int q = new GreedyMesher().mesh(s, c);
        assertEquals(0, q);
        assertEquals(0, c.count());
    }

    @Test
    void singleVoxelYieldsSixQuads() {
        Section s = new Section();
        s.set(15, 15, 15, STONE);
        QuadCounter c = new QuadCounter();
        int q = new GreedyMesher().mesh(s, c);
        assertEquals(6, q);
    }

    @Test
    void fullySolidSectionYieldsSixQuads() {
        Section s = new Section();
        for (int i = 0; i < Section.VOLUME; i++) s.setByIndex(i, STONE);
        QuadCounter c = new QuadCounter();
        int q = new GreedyMesher().mesh(s, c);
        assertEquals(6, q);
    }

    @Test
    void flatPlaneYieldsSixQuads() {
        Section s = new Section();
        for (int x = 0; x < Section.SIZE; x++) {
            for (int z = 0; z < Section.SIZE; z++) {
                s.set(x, 0, z, STONE);
            }
        }
        QuadCounter c = new QuadCounter();
        int q = new GreedyMesher().mesh(s, c);
        // Top +Y face 32x32 = 1 quad.
        // Bottom -Y face 32x32 = 1 quad.
        // 4 side faces (-X, +X, -Z, +Z): each 32×1 = 1 quad each = 4 quads.
        assertEquals(6, q);
    }

    @Test
    void checkerboardHasMaxIsolatedQuads() {
        Section s = new Section();
        int stoneCount = 0;
        for (int y = 0; y < Section.SIZE; y++) {
            for (int z = 0; z < Section.SIZE; z++) {
                for (int x = 0; x < Section.SIZE; x++) {
                    if (((x + y + z) & 1) == 0) {
                        s.set(x, y, z, STONE);
                        stoneCount++;
                    }
                }
            }
        }
        QuadCounter c = new QuadCounter();
        int q = new GreedyMesher().mesh(s, c);
        // Every stone voxel is isolated (all 6 neighbours are air) → greedy
        // cannot merge anything. 6 quads per stone.
        assertEquals(6 * stoneCount, q);
    }

    @Test
    void twoDifferentBlockstatesDoNotMerge() {
        Section s = new Section();
        // Two adjacent 1x1x1 cubes of different blockstates, open air everywhere else
        s.set(10, 10, 10, STONE);
        s.set(11, 10, 10, DIRT);
        QuadCounter c = new QuadCounter();
        int q = new GreedyMesher().mesh(s, c);
        // Stone: 5 visible faces (the shared face with dirt is internal, same)
        // Actually: stone's +X face touches dirt's -X face; both are solid so
        // neither is visible — 0 quads at that boundary.
        // Stone has 5 external visible faces; dirt has 5 external visible faces.
        // Top (+Y) faces of stone (1x1) and dirt (1x1) have different blockstates so
        // they will not merge into one 2x1 even though they are coplanar and contiguous.
        assertEquals(10, q);
    }

    @Test
    void matchesNaiveOnFullySolid() {
        Section s = new Section();
        for (int i = 0; i < Section.VOLUME; i++) s.setByIndex(i, STONE);
        QuadCounter gc = new QuadCounter();
        QuadCounter nc = new QuadCounter();
        int g = new GreedyMesher().mesh(s, gc);
        int n = new NaiveMesher().mesh(s, nc);
        // Greedy merges 32×32 per face → 6 total
        assertEquals(6, g);
        // Naive: surface has 6 × 32² = 6144 visible faces
        assertEquals(6144, n);
        assertTrue(n >= 100 * g);
    }

    @Test
    void greedyBeatsNaiveByFiveOnRealisticTerrain() {
        Section s = heightmapSection(0xBEEF_FEEDL);
        QuadCounter gc = new QuadCounter();
        QuadCounter nc = new QuadCounter();
        int g = new GreedyMesher().mesh(s, gc);
        int n = new NaiveMesher().mesh(s, nc);
        double ratio = (double) n / g;
        assertTrue(ratio >= 5.0,
            "greedy/naive ratio must be >= 5x on realistic terrain, got "
                + ratio + " (n=" + n + ", g=" + g + ")");
    }

    @Test
    void meshBuilderEmitsFourVerticesPerQuad() {
        Section s = new Section();
        s.set(0, 0, 0, STONE);
        MeshBuilder mb = new MeshBuilder();
        int q = new GreedyMesher().mesh(s, mb);
        assertEquals(6, q);
        assertEquals(6 * MeshFormat.QUAD_BYTES, mb.vertexByteCount());
    }

    /**
     * Realistic Minecraft-style terrain — plateaus of uniform height broken up
     * by a few step transitions, which is how real chunks look. Low-frequency
     * heightmap (coarse 8×8 grid, nearest-neighbor up-sampled to 32×32) means
     * greedy has contiguous flat regions to merge.
     */
    private static Section heightmapSection(long seed) {
        Random rnd = new Random(seed);
        int S = Section.SIZE;
        int COARSE = 8;
        int[][] coarse = new int[COARSE][COARSE];
        for (int i = 0; i < COARSE; i++) {
            for (int j = 0; j < COARSE; j++) {
                coarse[i][j] = 12 + rnd.nextInt(6); // heights 12..17
            }
        }
        int tile = S / COARSE;
        Section s = new Section();
        for (int x = 0; x < S; x++) {
            for (int z = 0; z < S; z++) {
                int h = coarse[x / tile][z / tile];
                for (int y = 0; y < h && y < S; y++) {
                    s.set(x, y, z, (y < h - 3) ? STONE : DIRT);
                }
            }
        }
        return s;
    }
}
