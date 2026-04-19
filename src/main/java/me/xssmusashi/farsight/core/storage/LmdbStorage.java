package me.xssmusashi.farsight.core.storage;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.ByteBuffer.allocateDirect;

/**
 * Minimal LMDB key/value store for voxel sections.
 *
 * <p>One database, {@code sections}, keyed by the 17-byte {@link SectionKey}
 * encoding. Values are the framed, zstd-compressed payloads produced by
 * {@link SectionCodec}.</p>
 *
 * <p>Thread-safety: readers may run concurrently with each other and with a
 * single writer. The class does not serialize writers — callers must ensure
 * only one writer thread at a time, or hold their own lock.</p>
 */
public final class LmdbStorage implements AutoCloseable {
    public static final long DEFAULT_MAP_SIZE = 1L << 36; // 64 GB virtual address reservation

    private final Env<ByteBuffer> env;
    private final Dbi<ByteBuffer> sections;
    private final Dbi<ByteBuffer> meshes;

    public LmdbStorage(Path directory) {
        this(directory, DEFAULT_MAP_SIZE);
    }

    public LmdbStorage(Path directory, long mapSize) {
        try {
            Files.createDirectories(directory);
        } catch (Exception e) {
            throw new RuntimeException("failed to create storage dir: " + directory, e);
        }
        this.env = Env.create()
                .setMapSize(mapSize)
                .setMaxDbs(4)
                .setMaxReaders(128)
                .open(directory.toFile(), EnvFlags.MDB_NOTLS, EnvFlags.MDB_NOSYNC);
        this.sections = env.openDbi("sections", DbiFlags.MDB_CREATE);
        this.meshes = env.openDbi("meshes", DbiFlags.MDB_CREATE);
    }

    public void put(SectionKey key, byte[] value) { put(sections, key, value); }
    public byte[] get(SectionKey key)              { return get(sections, key); }
    public boolean delete(SectionKey key)          { return delete(sections, key); }

    public void putMesh(SectionKey key, byte[] value) { put(meshes, key, value); }
    public byte[] getMesh(SectionKey key)              { return get(meshes, key); }
    public boolean deleteMesh(SectionKey key)          { return delete(meshes, key); }

    private void put(Dbi<ByteBuffer> dbi, SectionKey key, byte[] value) {
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            ByteBuffer keyBuf = allocateDirect(SectionKey.BYTES);
            key.writeTo(keyBuf);
            keyBuf.flip();
            ByteBuffer valBuf = allocateDirect(value.length);
            valBuf.put(value).flip();
            dbi.put(txn, keyBuf, valBuf);
            txn.commit();
        }
    }

    private byte[] get(Dbi<ByteBuffer> dbi, SectionKey key) {
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer keyBuf = allocateDirect(SectionKey.BYTES);
            key.writeTo(keyBuf);
            keyBuf.flip();
            ByteBuffer val = dbi.get(txn, keyBuf);
            if (val == null) return null;
            byte[] out = new byte[val.remaining()];
            val.duplicate().get(out);
            return out;
        }
    }

    private boolean delete(Dbi<ByteBuffer> dbi, SectionKey key) {
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            ByteBuffer keyBuf = allocateDirect(SectionKey.BYTES);
            key.writeTo(keyBuf);
            keyBuf.flip();
            boolean removed = dbi.delete(txn, keyBuf);
            txn.commit();
            return removed;
        }
    }

    public long approximateEntryCount() {
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            return sections.stat(txn).entries;
        }
    }

    public void sync(boolean force) {
        env.sync(force);
    }

    @Override
    public void close() {
        meshes.close();
        sections.close();
        env.close();
    }
}
