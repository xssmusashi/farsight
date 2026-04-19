package me.xssmusashi.farsight.ingest;

import me.xssmusashi.farsight.FarsightClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Bulk-loads a saved world's {@code region/*.mca} files into Farsight's
 * storage without having to re-explore the world in-game.
 *
 * <p>Scope in this pass is scaffold-only: the importer discovers region files
 * and reports what it would process. Actual NBT decoding of chunk sections
 * into {@link ChunkSnapshot}s is deferred because it requires full access to
 * Minecraft's {@code BlockState} / {@code Biome} registries, which are only
 * reliably populated on a live client with a loaded data pack. A future pass
 * will plug a {@code ChunkDecoder} behind this interface.</p>
 */
public final class RegionImporter {
    private static final Pattern REGION_NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    public record RegionFile(Path path, int regionX, int regionZ) {}

    public record ImportReport(int regionsFound, int regionsProcessed, int chunksIngested, int errors) {
        public String summary() {
            return String.format(
                "regions=%d processed=%d chunks=%d errors=%d",
                regionsFound, regionsProcessed, chunksIngested, errors);
        }
    }

    private RegionImporter() {}

    /** Enumerates {@code r.X.Z.mca} files in {@code worldDir/region/}. */
    public static List<RegionFile> listRegions(Path worldDir) throws IOException {
        Path regionDir = worldDir.resolve("region");
        if (!Files.isDirectory(regionDir)) return List.of();
        List<RegionFile> out = new ArrayList<>();
        try (Stream<Path> stream = Files.list(regionDir)) {
            stream.filter(Files::isRegularFile)
                  .forEach(p -> {
                      Matcher m = REGION_NAME.matcher(p.getFileName().toString());
                      if (m.matches()) {
                          int rx = Integer.parseInt(m.group(1));
                          int rz = Integer.parseInt(m.group(2));
                          out.add(new RegionFile(p, rx, rz));
                      }
                  });
        }
        return out;
    }

    /**
     * Scaffold-level import: enumerates region files and logs what would be
     * ingested. Returns an {@link ImportReport} with zero chunks until the
     * decoder is wired up.
     */
    public static ImportReport importWorld(Path worldDir, ChunkIngestor ingestor) throws IOException {
        List<RegionFile> regions = listRegions(worldDir);
        FarsightClient.LOGGER.info("region importer: found {} region files under {}", regions.size(), worldDir);
        int processed = 0;
        int errors = 0;
        for (RegionFile r : regions) {
            try {
                // TODO: open r.path() with net.minecraft.world.level.chunk.storage.RegionFile,
                //       iterate its 32x32 chunk slots, for each chunk tag extract
                //       block_states + biomes per section, transpose into 32³ Farsight
                //       sections, feed to ingestor.submit(...).
                FarsightClient.LOGGER.debug("would process region {} ({}x{})", r.path(), r.regionX(), r.regionZ());
                processed++;
            } catch (RuntimeException e) {
                errors++;
                FarsightClient.LOGGER.warn("region {} failed", r.path(), e);
            }
        }
        return new ImportReport(regions.size(), processed, 0, errors);
    }
}
