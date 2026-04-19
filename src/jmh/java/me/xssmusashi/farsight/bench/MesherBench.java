package me.xssmusashi.farsight.bench;

import me.xssmusashi.farsight.core.mesh.GreedyMesher;
import me.xssmusashi.farsight.core.mesh.MeshBuilder;
import me.xssmusashi.farsight.core.mesh.NaiveMesher;
import me.xssmusashi.farsight.core.mesh.QuadCounter;
import me.xssmusashi.farsight.core.voxel.Section;
import me.xssmusashi.farsight.core.voxel.VoxelEntry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 3)
@Fork(1)
public class MesherBench {

    @Param({"solid", "noisy-terrain", "sparse"})
    public String scene;

    private Section section;
    private GreedyMesher greedy;
    private NaiveMesher naive;
    private QuadCounter counter;

    @Setup
    public void setup() {
        greedy = new GreedyMesher();
        naive = new NaiveMesher();
        counter = new QuadCounter();

        Random rnd = new Random(0xABCDEF01L);
        long stone = VoxelEntry.encode(1, 0, 0, VoxelEntry.FLAG_SOLID, 0);
        long dirt = VoxelEntry.encode(2, 0, 0, VoxelEntry.FLAG_SOLID, 0);

        section = new Section();
        switch (scene) {
            case "solid" -> {
                for (int i = 0; i < Section.VOLUME; i++) section.setByIndex(i, stone);
            }
            case "noisy-terrain" -> {
                for (int y = 0; y < Section.SIZE; y++) {
                    for (int z = 0; z < Section.SIZE; z++) {
                        for (int x = 0; x < Section.SIZE; x++) {
                            float density = Math.max(0f, 0.95f - y / (float) Section.SIZE);
                            if (rnd.nextFloat() < density) {
                                section.set(x, y, z, rnd.nextFloat() < 0.5f ? stone : dirt);
                            }
                        }
                    }
                }
            }
            case "sparse" -> {
                for (int i = 0; i < Section.VOLUME; i++) {
                    if (rnd.nextFloat() < 0.05f) section.setByIndex(i, stone);
                }
            }
            default -> throw new IllegalStateException("unknown scene " + scene);
        }
    }

    @Benchmark
    public int greedyCount() {
        counter.reset();
        return greedy.mesh(section, counter);
    }

    @Benchmark
    public int naiveCount() {
        counter.reset();
        return naive.mesh(section, counter);
    }

    @Benchmark
    public int greedyToMeshBuilder(Blackhole bh) {
        MeshBuilder mb = new MeshBuilder();
        int q = greedy.mesh(section, mb);
        bh.consume(mb.vertexByteCount());
        return q;
    }
}
