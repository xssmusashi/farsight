# Farsight

Extreme render-distance mod for Minecraft Fabric. Sparse voxel octree LoDs with
GPU-driven rendering.

**Status:** alpha / work-in-progress. Builds, no in-game verification yet.

## Targets

| item | value |
| --- | --- |
| Minecraft | 26.1.1 |
| Fabric Loader | 0.18.6 |
| Fabric API | 0.145.3+26.1.1 |
| Java | 25 |
| Loom | 1.15-SNAPSHOT |
| Gradle | 9.4.1 |

## Architecture (planned)

- **Voxel data**: 32³ sections, 6 LoD levels (native → 1:32).
- **Storage**: LMDB + zstd framing.
- **Meshing**: CPU greedy mesher (Lysenko), pre-baked at ingest-time.
- **GPU**: OpenGL 4.6, bindless textures, persistent-mapped SSBOs,
  `glMultiDrawElementsIndirectCount`, compute-shader frustum + HiZ culling.

See `STATUS.md` for what currently works.

## Build

```bash
./gradlew build
```

## Credits

Independent MIT-licensed implementation; inspired by techniques described in
voxel-rendering literature (greedy meshing by Mikola Lysenko, GPU-driven
rendering papers, bindless-texture and indirect-draw OpenGL extensions).
