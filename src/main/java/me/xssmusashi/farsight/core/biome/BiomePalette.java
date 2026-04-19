package me.xssmusashi.farsight.core.biome;

import java.util.Arrays;

/**
 * Maps {@link me.xssmusashi.farsight.core.voxel.VoxelEntry} biome IDs (0..4095)
 * to a 0xRRGGBB colour. Used by the fragment shader as a tint on the
 * base per-blockstate colour.
 *
 * <p>The default palette is loosely modelled on Minecraft's broad biome
 * families — plains are warm green, taiga is cool green, desert is pale
 * yellow, etc. Real biome colour data would live in a resource file loaded
 * at world-join time; this default keeps the renderer producing sensible
 * output when that resource is missing.</p>
 */
public final class BiomePalette {
    public static final int DEFAULT_COLOR = 0x808080;
    private final int[] colors = new int[4096];

    public BiomePalette() {
        Arrays.fill(colors, DEFAULT_COLOR);
    }

    public int colorOf(int biomeId) {
        if (biomeId < 0 || biomeId >= colors.length) return DEFAULT_COLOR;
        return colors[biomeId];
    }

    public BiomePalette set(int biomeId, int color) {
        colors[biomeId] = color & 0xFFFFFF;
        return this;
    }

    public int[] rawColors() {
        return colors.clone();
    }

    /** Broad-family defaults keyed by small biome IDs, useful as a baseline. */
    public static BiomePalette defaults() {
        return new BiomePalette()
            .set(0, 0x79B959)   // plains
            .set(1, 0x8DB360)   // forest
            .set(2, 0x86B87F)   // taiga
            .set(3, 0xF4F4E4)   // desert
            .set(4, 0xB9D0B9)   // swamp
            .set(5, 0x3B6ECC)   // ocean
            .set(6, 0x8E8C7B)   // mountains / stony
            .set(7, 0xB4E6A0)   // jungle
            .set(8, 0xD9C7AA)   // savanna
            .set(9, 0xCDE0E4)   // snowy tundra
            .set(10, 0xA86B36)  // badlands / mesa
            .set(11, 0x50C878)  // lush caves
            .set(12, 0xFF6347)  // nether (wastes)
            .set(13, 0x3F005F); // end
    }
}
