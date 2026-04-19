# Farsight status

_Last updated 2026-04-19._

## What works

- Phase 0 skeleton: Gradle project builds.

## What does not

- Everything else. No voxel ingest, no meshing, no GPU pipeline yet.

## Phase progress

- [x] Phase 0 — Skeleton
- [ ] Phase 1 — Voxel data model + LMDB storage
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
