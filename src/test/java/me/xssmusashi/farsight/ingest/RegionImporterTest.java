package me.xssmusashi.farsight.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegionImporterTest {

    @Test
    void listRegionsReturnsEmptyWhenNoRegionDir(@TempDir Path world) throws IOException {
        List<RegionImporter.RegionFile> regions = RegionImporter.listRegions(world);
        assertTrue(regions.isEmpty());
    }

    @Test
    void listRegionsPicksUpMcaFiles(@TempDir Path world) throws IOException {
        Path regionDir = world.resolve("region");
        Files.createDirectories(regionDir);
        Files.createFile(regionDir.resolve("r.0.0.mca"));
        Files.createFile(regionDir.resolve("r.-3.5.mca"));
        Files.createFile(regionDir.resolve("notaregion.txt"));

        List<RegionImporter.RegionFile> regions = RegionImporter.listRegions(world);
        assertEquals(2, regions.size());
        assertTrue(regions.stream().anyMatch(r -> r.regionX() == 0 && r.regionZ() == 0));
        assertTrue(regions.stream().anyMatch(r -> r.regionX() == -3 && r.regionZ() == 5));
    }

    @Test
    void importWorldScaffoldReportsCounts(@TempDir Path world) throws IOException {
        Path regionDir = world.resolve("region");
        Files.createDirectories(regionDir);
        Files.createFile(regionDir.resolve("r.0.0.mca"));
        Files.createFile(regionDir.resolve("r.1.0.mca"));

        RegionImporter.ImportReport report = RegionImporter.importWorld(world, null);
        assertEquals(2, report.regionsFound());
        assertEquals(2, report.regionsProcessed());
        assertEquals(0, report.chunksIngested()); // Scaffold — decoder not wired yet
        assertEquals(0, report.errors());
    }
}
