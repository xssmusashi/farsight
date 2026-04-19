package me.xssmusashi.farsight.ingest;

import me.xssmusashi.farsight.core.storage.SectionKey;
import me.xssmusashi.farsight.core.voxel.Section;

/**
 * Immutable snapshot of one 32³ volume ready to be ingested. Produced by the
 * Minecraft integration layer from a freshly loaded chunk (or from test
 * fixtures). Carries the section key plus a dense voxel array.
 *
 * <p>Voxels are stored as packed {@link me.xssmusashi.farsight.core.voxel.VoxelEntry}
 * longs, indexed y-major-z-mid-x-minor (see {@link Section#index}).</p>
 */
public record ChunkSnapshot(SectionKey key, long[] voxels) {
    public ChunkSnapshot {
        if (voxels.length != Section.VOLUME) {
            throw new IllegalArgumentException("expected " + Section.VOLUME + " voxels, got " + voxels.length);
        }
    }

    public Section toSection() {
        Section s = new Section();
        for (int i = 0; i < Section.VOLUME; i++) {
            s.setByIndex(i, voxels[i]);
        }
        return s;
    }
}
