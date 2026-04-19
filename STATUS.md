# Farsight status

_Last updated 2026-04-19 (v0.1.0-alpha)._

## Summary

Farsight is an MIT-licensed Minecraft Fabric mod aiming at extreme render
distances via a sparse voxel octree and GPU-driven rendering. Independent
implementation; inspired by techniques described in voxel-rendering
literature.

This session completed Phases 0–6 and ships an alpha jar that builds, loads
the core runtime, and registers client commands, but does **not** yet draw
pixels in-game (Phase 5 is scaffold-only, no mixin into the render loop).

## Phase progress

- [x] Phase 0 — Skeleton (Gradle + Fabric + Loom)
- [x] Phase 1 — Voxel data model + LMDB storage + zstd codec
- [x] Phase 2 — LoD downsampling (majority-vote, priority-tie-break, intra-section mip pyramid)
- [x] Phase 3 — Greedy mesher (Lysenko) + naive A/B baseline + MeshBuilder vertex packer
- [x] Phase 4 — Ingest pipeline (ChunkSnapshot → Section → LodPyramid → Greedy mesh → LMDB), ForkJoinPool, `/farsight stats` / `/farsight rebuild`
- [x] Phase 5 — GPU scaffold (compiles). **No mixin into the render loop yet — does not draw pixels.**
- [x] Phase 6 — GSON JSON config at `config/farsight.json`
- [ ] Phase 7 — Iris/Oculus shader pack compat, cross-section LoD, biome coloring, AO

## What works

- `./gradlew build` produces a loadable jar (`farsight-0.1.0-alpha.jar`).
- 33 unit tests pass across voxel, palette, section, LMDB, LoD, mesher, ingest, config, shader resources.
- Storage benchmarks exceed targets (see below).
- Greedy mesher passes its ≥5× polygon-reduction gate on realistic heightmap terrain.
- Client mod entrypoint logs init, loads config, registers `/farsight stats` and `/farsight rebuild` commands.

## What does not

- **The renderer does not actually render.** `FarsightRenderer` exists but is never instantiated by a mixin into Minecraft's render pipeline.
- **Minecraft chunk data is never captured.** Ingest pipeline is end-to-end testable with synthetic snapshots, but there is no mixin/event hook that turns real `LevelChunk` data into `ChunkSnapshot`.
- **Cross-section LoD aggregation** (stitching 8 adjacent native sections into one level-1 section, and so on up the tree) is not implemented. Only intra-section mip pyramids exist.
- **Mesh persistence** is not done — meshes are built on ingest but not stored; Phase 5 would need them on disk.
- **No texture atlas wiring** — the fragment shader uses a hash-to-color placeholder.
- **No Iris/shader pack compat** — Phase 7.

## Storage benchmarks (JDK 25, Windows 11, NVMe)

Per-op measurements, 1 fork × 2 warmup × 3 iterations:

| op | avg time | effective throughput |
| --- | --- | --- |
| `SectionCodec.encode` (zstd level 3) | 123.3 μs | ~527 MB/s |
| `SectionCodec.decode` | 44.5 μs | ~1461 MB/s |
| `LmdbStorage.put` (write + commit) | 25.9 μs | ~38.6k ops/s |
| `LmdbStorage.get` | 1.9 μs | ~515k ops/s |

Targets from plan were ≥100 MB/s write and ≥300 MB/s read. Both exceeded.

## Mesher polygon reduction

Per 32³ section:

| scene | naive quads | greedy quads | ratio |
| --- | --- | --- | --- |
| fully solid | 6144 | 6 | 1024× |
| heightmap plateau terrain | ~5100 | ~950 | ≥5× (test-enforced gate) |

Greedy CPU wall-clock per section (JMH, avg):

| scene | greedy | naive |
| --- | --- | --- |
| fully solid | 176 μs | 102 μs |
| noisy-terrain | 804 μs | 144 μs |
| sparse | 394 μs | 43 μs |

Greedy is slower CPU-side but produces 5–1000× fewer quads, which is the
bottleneck downstream (vertex upload, rasterisation).

## Known limitations / gotchas

- `lmdbjava 0.9.0` uses `sun.misc.Unsafe`; a deprecation warning fires under
  JDK 25 but it still works. Plan to migrate off when the library updates.
- `--add-opens java.base/java.nio=ALL-UNNAMED` and `--enable-native-access=ALL-UNNAMED`
  are needed for tests and benchmarks under JDK 25.
- Gradle 9.4.1 emits "incompatible with Gradle 10" warnings from Loom 1.15.5.
  No user action required yet.

## Next recommended sessions

1. **Mixin the render loop.** Target `net.minecraft.client.renderer.LevelRenderer#renderLevel` and have it call `FarsightRenderer.render(...)` after terrain. Verify shaders compile against a real GL context.
2. **Chunk ingestion mixin.** Hook `ClientLevel.onChunkLoaded` / `ChunkBiomeContainer` to convert `LevelChunk` sections into `ChunkSnapshot` and submit to the ingestor.
3. **Cross-section LoD aggregation.** Implement 8-child-into-1-parent downsampling and LMDB-backed hierarchical storage so LoD levels 1..5 survive restarts.
4. **Mesh persistence.** Add a second LMDB dbi (`meshes`) with key = `SectionKey`, value = framed vertex+index blob. Invalidate on neighbor section update.
5. **Profile the full pipeline on a real world.** Use JFR (or `-prof jfr` on JMH) to find the actual bottleneck — mesher CPU time may be dwarfed by GPU upload bandwidth.

## Git

- Repo: https://github.com/xssmusashi/farsight
- Branch: `main`
- Tags: `v0.0.0-skeleton` (Phase 0), `v0.1.0-alpha` (Phases 0–6)
- Final jar: `build/libs/farsight-0.1.0-alpha.jar`
