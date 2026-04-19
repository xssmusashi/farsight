package me.xssmusashi.farsight.core.storage;

/**
 * Abstract key/value store for voxel sections and baked meshes. Two backends
 * exist: {@link LmdbStorage} (preferred — memory-mapped, high throughput,
 * needs {@code --add-opens java.base/java.nio=ALL-UNNAMED} or a successful
 * {@link me.xssmusashi.farsight.boot.ModuleUnlocker}) and
 * {@link FilesystemStorage} (fallback — one file per key, no native deps,
 * works on any JVM configuration).
 *
 * <p>{@link me.xssmusashi.farsight.world.WorldSession} picks the backend at
 * construct time: tries LMDB, catches any failure, falls back to filesystem.</p>
 */
public interface SectionStore extends AutoCloseable {

    void put(SectionKey key, byte[] value);
    byte[] get(SectionKey key);
    boolean delete(SectionKey key);

    void putMesh(SectionKey key, byte[] value);
    byte[] getMesh(SectionKey key);
    boolean deleteMesh(SectionKey key);

    long approximateEntryCount();
    void sync(boolean force);

    @Override
    void close();
}
