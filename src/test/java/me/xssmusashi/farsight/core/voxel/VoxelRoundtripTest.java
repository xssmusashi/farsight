package me.xssmusashi.farsight.core.voxel;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class VoxelRoundtripTest {

    @Test
    void encodeDecodeExtremes() {
        long entry = VoxelEntry.encode(
                VoxelEntry.MAX_BLOCKSTATE,
                VoxelEntry.MAX_BIOME,
                0xFF,
                0xFF,
                0xFFF);
        assertEquals(VoxelEntry.MAX_BLOCKSTATE, VoxelEntry.blockstate(entry));
        assertEquals(VoxelEntry.MAX_BIOME, VoxelEntry.biome(entry));
        assertEquals(0xFF, VoxelEntry.light(entry));
        assertEquals(0xFF, VoxelEntry.flags(entry));
        assertEquals(0xFFF, VoxelEntry.normalPacked(entry));
    }

    @Test
    void airIsZero() {
        assertTrue(VoxelEntry.isAir(VoxelEntry.AIR));
        assertEquals(0, VoxelEntry.blockstate(VoxelEntry.AIR));
    }

    @Test
    void fieldsAreIndependent() {
        Random rnd = new Random(0xFA25E144L);
        for (int i = 0; i < 10_000; i++) {
            int bs = rnd.nextInt(VoxelEntry.MAX_BLOCKSTATE + 1);
            int bi = rnd.nextInt(VoxelEntry.MAX_BIOME + 1);
            int li = rnd.nextInt(256);
            int fl = rnd.nextInt(256);
            int nr = rnd.nextInt(4096);
            long e = VoxelEntry.encode(bs, bi, li, fl, nr);
            assertEquals(bs, VoxelEntry.blockstate(e));
            assertEquals(bi, VoxelEntry.biome(e));
            assertEquals(li, VoxelEntry.light(e));
            assertEquals(fl, VoxelEntry.flags(e));
            assertEquals(nr, VoxelEntry.normalPacked(e));
        }
    }

    @Test
    void paletteDedupes() {
        Palette p = new Palette();
        long a = VoxelEntry.encode(1, 0, 0, 0, 0);
        long b = VoxelEntry.encode(2, 0, 0, 0, 0);
        assertEquals(0, p.indexOf(VoxelEntry.AIR));
        int ia = p.add(a);
        int ib = p.add(b);
        assertEquals(ia, p.add(a));
        assertNotEquals(ia, ib);
        assertEquals(3, p.size());
        assertEquals(a, p.get(ia));
        assertEquals(b, p.get(ib));
    }

    @Test
    void sectionRoundtripRandom() {
        Random rnd = new Random(0xDEADBEEFL);
        Section s = new Section();
        for (int i = 0; i < Section.VOLUME; i++) {
            if (rnd.nextFloat() < 0.3f) {
                int bs = 1 + rnd.nextInt(128);
                s.setByIndex(i, VoxelEntry.encode(bs, 0, 0, VoxelEntry.FLAG_SOLID, 0));
            }
        }
        byte[] bytes = s.serialize();
        Section round = Section.deserialize(bytes);
        assertEquals(s, round);
    }

    @Test
    void sectionCheckerboardHasTwoPaletteEntries() {
        Section s = new Section();
        long stone = VoxelEntry.encode(1, 0, 0, VoxelEntry.FLAG_SOLID, 0);
        for (int y = 0; y < Section.SIZE; y++) {
            for (int z = 0; z < Section.SIZE; z++) {
                for (int x = 0; x < Section.SIZE; x++) {
                    if (((x + y + z) & 1) == 0) s.set(x, y, z, stone);
                }
            }
        }
        assertEquals(2, s.palette().size());
    }
}
