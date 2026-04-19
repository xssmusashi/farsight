# Farsight status

_Last updated 2026-04-19._

## What works

- Phase 0 skeleton: Gradle project builds, jar produced.
- Phase 1 voxel + storage:
  - `VoxelEntry` packed 64-bit encoding (24/12/8/8/12 = blockstate/biome/light/flags/normal).
  - `Palette` with dedup, up to 4096 entries per section, AIR reserved at index 0.
  - `Section` 32³ with y-major-z-mid-x-minor indexing, serialize/deserialize.
  - `SectionCodec` zstd + `FRSV` magic header + CRC32.
  - `LmdbStorage` 17-byte keys, env lifetime management.
  - 10 unit tests passing.

## Storage benchmarks (JDK 25, Windows, NVMe)

Per-op measurements, 1 fork × 2 warmup × 3 iterations:

| op | avg time | throughput |
| --- | --- | --- |
| `SectionCodec.encode` (zstd level 3) | 123.3 μs | ~527 MB/s |
| `SectionCodec.decode` | 44.5 μs | ~1461 MB/s |
| `LmdbStorage.put` (write + commit) | 25.9 μs | ~38.6k ops/s |
| `LmdbStorage.get` | 1.9 μs | ~515k ops/s |

Exceeds targets from plan (≥100 MB/s write, ≥300 MB/s read).

## What does not

- No LoD downsampling yet.
- No meshing.
- No ingest pipeline, no GPU.

## Phase progress

- [x] Phase 0 — Skeleton
- [x] Phase 1 — Voxel data model + LMDB storage
- [ ] Phase 2 — LoD downsampling
- [ ] Phase 2 — LoD downsampling
- [ ] Phase 3 — Greedy mesher
- [ ] Phase 4 — Chunk ingest pipeline
- [ ] Phase 5 — GPU render pipeline
- [ ] Phase 6 — Config + polish

## Known blockers

None yet.

## Next session recommendations

1. Run client in dev env (`./gradlew runClient`) once Phase 4 lands.
2. Profile mesher on realistic chunks before committing to the algorithm.
3. Iris/Oculus shader-pack compat is Phase 7 — ~3 days of work alone.
