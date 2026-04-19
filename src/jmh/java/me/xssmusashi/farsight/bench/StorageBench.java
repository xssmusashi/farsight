package me.xssmusashi.farsight.bench;

import me.xssmusashi.farsight.core.storage.LmdbStorage;
import me.xssmusashi.farsight.core.storage.SectionCodec;
import me.xssmusashi.farsight.core.storage.SectionKey;
import me.xssmusashi.farsight.core.voxel.Section;
import me.xssmusashi.farsight.core.voxel.VoxelEntry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 3)
@Fork(1)
public class StorageBench {

    private Path dir;
    private LmdbStorage store;
    private byte[] framedValue;
    private Section section;
    private SectionKey[] keys;
    private static final int KEY_COUNT = 1000;

    @Setup
    public void setup() throws IOException {
        dir = Files.createTempDirectory("farsight-bench");
        store = new LmdbStorage(dir, 1L << 32);
        Random rnd = new Random(0xBEEFFEEDL);
        section = new Section();
        long stone = VoxelEntry.encode(1, 0, 0, VoxelEntry.FLAG_SOLID, 0);
        long dirt = VoxelEntry.encode(2, 0, 0, VoxelEntry.FLAG_SOLID, 0);
        long grass = VoxelEntry.encode(3, 0, 0, VoxelEntry.FLAG_SOLID, 0);
        for (int i = 0; i < Section.VOLUME; i++) {
            float p = rnd.nextFloat();
            if (p < 0.25f) section.setByIndex(i, stone);
            else if (p < 0.40f) section.setByIndex(i, dirt);
            else if (p < 0.42f) section.setByIndex(i, grass);
        }
        framedValue = SectionCodec.encode(section.serialize());

        keys = new SectionKey[KEY_COUNT];
        for (int i = 0; i < KEY_COUNT; i++) {
            keys[i] = new SectionKey(0, i, i >> 2, -i, 0);
            store.put(keys[i], framedValue);
        }
    }

    @TearDown
    public void tearDown() throws IOException {
        store.close();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }

    @Benchmark
    public void put(Blackhole bh) {
        SectionKey k = new SectionKey(1, (int) (System.nanoTime() & 0xFFFF), 0, 0, 0);
        store.put(k, framedValue);
        bh.consume(k);
    }

    @Benchmark
    public byte[] get() {
        return store.get(keys[0]);
    }

    @Benchmark
    public byte[] getRandom(Blackhole bh) {
        int i = (int) (System.nanoTime() & (KEY_COUNT - 1));
        byte[] v = store.get(keys[i]);
        bh.consume(v);
        return v;
    }

    @Benchmark
    public byte[] encode() {
        return SectionCodec.encode(section.serialize());
    }

    @Benchmark
    public byte[] decode() {
        return SectionCodec.decode(framedValue);
    }
}
