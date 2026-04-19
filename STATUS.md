# Farsight status

_Last updated 2026-04-20 (v0.1.6-alpha)._

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
- [~] Phase 7 — in progress:
  - Done: per-face AO, biome palette, `SectionNeighborhood` scaffold for LoD skirts, updated shaders (AO + biome-tint aware).
  - Done: **Iris compat detection + scaffolding** — reflective public-API probe (`IrisCompatibility`), `ShaderOverrides` for `gbuffers_farsight_lod.{vsh,fsh}` pack customization, shader-pack templates under `assets/farsight/shaders/compat/`.
  - Done: **Real Iris integration against internal API (Iris 1.10.9 / MC 26.1.1):** Iris added as `compileOnly` (not shipped). New `render/iris/RealIrisAdapter` uses direct Iris imports: walks `Iris.getPipelineManager().getPipelineNullable()`, casts to `IrisRenderingPipeline`, calls `ShaderRenderingPipeline.getShaderMap().getShader(ShaderKey.TERRAIN_SOLID)` → `GlProgram.getProgramId()` for the compiled terrain program, reflects into `ExtendedShader.writingToBeforeTranslucent` for the current gbuffer `GlFramebuffer`, and `bindAsDrawBuffer()`s it so Farsight's draws hit the same MRT targets that Iris's composite passes read. All of this is reflectively loaded by `IrisAdapter.resolve()` — if Iris is absent at runtime the inactive fallback takes over with zero risk of `NoClassDefFoundError` leaking out.
  - Done: **Hot-reload detection via `PipelineWatcher`** — polls pipeline identity via reflection every frame and triggers `refreshIrisAdapter()` when the reference changes. On refresh, the section program is recompiled from the newly-resolved pack; compile failures log and keep the old program.
  - Done: **Per-pack shader source resolution** — `IrisAdapter.shaderPackRoot()` reflectively resolves `Iris.getShaderpacksDirectory() / <active-pack-name> / shaders` via a fallback chain (`getIrisConfig().getShaderPackName()` → `Iris.getCurrentPackName()` → null). Pack-supplied `gbuffers_farsight_lod.{vsh,fsh}` are picked up automatically and survive hot-swap.
  - Done: **Cross-section LoD aggregation** — `core/lod/CrossSectionDownsampler` combines 8 adjacent sections (at level `L`) into one parent section (at level `L+1`) by materialising the 64³ dense cube and calling the existing `Downsampler.downsampleCube`; majority-vote + priority tie-break rules are reused verbatim so cross-section downsampling stays behaviourally identical to the intra-section mip pyramid. 5 unit tests.
  - Done: **Region importer scaffold** — `ingest/RegionImporter` discovers `<world>/region/r.X.Z.mca` files and reports them; `/farsight import <path>` client command wired. NBT decoding of chunk sections into `ChunkSnapshot`s is the last deferred piece — it needs live access to Minecraft's block state / biome registries, cleaner to do behind a mixin that runs on a live client. 3 unit tests for path discovery and `.mca` name parsing.

## What works

- `./gradlew build` produces a loadable jar (`farsight-0.1.0-alpha.jar`).
- 63 unit tests pass across voxel, palette, section, LMDB, LoD (intra- and cross-section), mesher, AO, biome palette, ingest, config, region discovery, mesh blob encode/decode, shader resources, Iris compat probe, shader overrides, pipeline watcher, world session lifecycle.
- Storage benchmarks exceed targets (see below).
- Greedy mesher passes its ≥5× polygon-reduction gate on realistic heightmap terrain.
- Client mod entrypoint logs init, loads config, registers `/farsight stats` and `/farsight rebuild` commands.

## v0.1.6 additions — live render pipeline

- **`LmdbStorage`** now hosts two DBIs: `sections` (voxel data) and `meshes` (pre-baked vertex blobs). Section-Key is reused for both; writes/reads share the same write transaction semantics.
- **`MeshBlob`** — compact on-disk record (`u32 quadCount`, `i32 originXYZ`, `f32 voxelScale`, raw vertex bytes) with explicit encode/decode + 3 unit tests.
- **`ChunkIngestor.process`** now bakes the native-level greedy mesh with AO + biome context, persists it via `LmdbStorage.putMesh`, and publishes the key to `PendingSections.QUEUE` — a lockfree MPSC (`jctools.MpscArrayQueue`) that bridges the ingest ForkJoinPool (many producers) to the single render thread (one consumer).
- **`QuadIndexBuffer`** — a single shared `GL_ELEMENT_ARRAY_BUFFER` pre-filled with the `[0,1,2, 0,2,3]` pattern for up to 4096 quads; all sections reuse it via `baseVertex` on the indirect-draw command.
- **`SectionRegistry`** — persistent-mapped SSBO of `{vec4 aabbMin(origin, voxelScale), vec4 aabbMax(origin+extent, baseVertex-bits)}` records. Free-slot bitmap keeps section IDs stable so the culling compute's `baseInstance` output and the vertex shader's `gl_BaseInstance` lookup line up.
- **`SectionLoader`** — render-thread consumer of `PendingSections.QUEUE`. Each frame, drains up to 8 keys, reads the mesh blob from the active `WorldSession.storage()`, allocates in the `SectionVboPool`, and registers an AABB slot in the registry.
- **`FarsightRenderer.drawFrame`** — full GPU-driven pass: dispatches `CullingCompute` (input = SectionRecord SSBO, output = indirect commands + atomic counter), binds the VAO/mega-VBO/registry SSBO, runs the section shader with `u_viewProj`, and issues one `glMultiDrawElementsIndirectCount` that covers every surviving section in the frame.
- **`FarsightFrameState`** — `AtomicReference<Frame>` holder; written by the mixin right before each `onFrame`, read by the renderer in the same call. Keeps the matrices off any thread-unsafe shared field.
- **`LevelRendererMixin`** — switched from a zero-arg `CallbackInfo`-only handler to the full 10-arg signature so it can capture `positionMatrix` and `projectionMatrix`. `require = 0` still protects mod load from descriptor drift.
- **`section.vert`** now reads per-section origin + voxelScale from the Registry SSBO (`binding = 2`) using `gl_BaseInstance` (via `GL_ARB_shader_draw_parameters`, GL 4.6 core) — one uniform (`u_viewProj`) does the whole per-frame state.

## Earlier: v0.1.5 additions — wiring into Minecraft

- **`LevelRendererMixin`** — `@Inject(method = "renderLevel", at = @At("HEAD"), require = 0)` calls `FarsightRenderHook.onFrame()` every frame. `require = 0` means descriptor drift in 26.1.x point releases logs a warning instead of crashing mod load; Farsight still loads and ingests, only the render pass sits idle.
- **`FarsightRenderHook`** — wraps `FarsightRenderer.ensureInitialised()` + `beginFrame()` in one try/catch. First exception permanently disables the hook for the session (no loop-crash inside the render thread).
- **`WorldLifecycle`** — registers `ClientPlayConnectionEvents.JOIN/DISCONNECT`. On join, creates a `WorldSession` (LmdbStorage + ChunkIngestor rooted under `<gameDir>/farsight-cache/<worldId>`) and publishes the ingestor via `FarsightClient.ACTIVE_INGESTOR`. On disconnect, closes cleanly.
- **`WorldIdentifier`** — derives a cache dir key: single-player uses the save's level name (`sp.getWorldData().getLevelName()`), multiplayer uses the sanitised server address.
- **`ChunkObserver`** — hooks `ClientChunkEvents.CHUNK_LOAD`. For each MC `LevelChunkSection`, packs block states into a `ChunkSnapshot` occupying the lower 16×16×16 octant of a Farsight 32³ (upper half stays air for this pass — cross-chunk aggregation is the next refinement). Skips `hasOnlyAir()` sections, submits the rest to the ingestor.
- **`BlockStateMapper`** — `BlockState` → packed voxel entry. Uses `Block.BLOCK_STATE_REGISTRY.getId(state)` truncated to 24 bits, derives flags from `getFluidState()` / `isSolid()`.

## What does not (as of v0.1.6)

- **Camera position uniform** — Mojang renamed the position accessors off `Camera` in 26.1.1 and the exact replacement hasn't been pinned down; the mixin passes {0,0,0} as camera pos, so the fragment shader's fog distance is measured from the world origin until that's fixed. Visual weirdness only — geometry still renders.
- **Hi-Z depth pyramid** is never built; the culling compute receives a placeholder 1×1 texture handle. Frustum culling works, occlusion culling is effectively a no-op.
- **Sparse Farsight sections.** Chunk ingest still produces sections with content only in the lower octant; 4 MC chunks → 1 full 32³ Farsight section is the next refinement.
- **No biome IDs yet.** `BlockStateMapper.toVoxel(state, 0)` — biome is always 0 until the registry lookup is wired.
- **`BiomeColors` SSBO at binding 1 is not uploaded** — fragment shader reads zero-sized SSBO, `biomeColors[biomeId]` returns `vec3(0.5)`. Palette upload pass is Session C.
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
