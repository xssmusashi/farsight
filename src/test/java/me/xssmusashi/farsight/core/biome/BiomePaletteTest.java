package me.xssmusashi.farsight.core.biome;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BiomePaletteTest {

    @Test
    void unsetBiomeReturnsDefaultColor() {
        BiomePalette p = new BiomePalette();
        assertEquals(BiomePalette.DEFAULT_COLOR, p.colorOf(0));
        assertEquals(BiomePalette.DEFAULT_COLOR, p.colorOf(4095));
    }

    @Test
    void setAndGetRoundTrips() {
        BiomePalette p = new BiomePalette().set(42, 0xFF00FF);
        assertEquals(0xFF00FF, p.colorOf(42));
    }

    @Test
    void outOfRangeReturnsDefaultColor() {
        BiomePalette p = new BiomePalette();
        assertEquals(BiomePalette.DEFAULT_COLOR, p.colorOf(-1));
        assertEquals(BiomePalette.DEFAULT_COLOR, p.colorOf(99_999));
    }

    @Test
    void defaultsPopulatePlainsAndOcean() {
        BiomePalette p = BiomePalette.defaults();
        assertEquals(0x79B959, p.colorOf(0));
        assertEquals(0x3B6ECC, p.colorOf(5));
    }
}
