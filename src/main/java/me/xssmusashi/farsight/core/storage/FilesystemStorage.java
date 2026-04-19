package me.xssmusashi.farsight.core.storage;

import me.xssmusashi.farsight.FarsightClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Flat-file fallback {@link SectionStore}. One file per (namespace, key) at
 * {@code <root>/<namespace>/<dim>_<sx>_<sy>_<sz>_<level>.bin}. No external
 * deps, no reflection — works on any JVM out of the box.
 *
 * <p>Trade-offs vs. LMDB:</p>
 * <ul>
 *   <li>Writes go through {@link Files#write} with an atomic rename so
 *       partial writes can't corrupt readers.</li>
 *   <li>Reads are one {@link Files#readAllBytes} per lookup — no memory
 *       mapping, no prefetch. Expect 5-20× higher latency than LMDB on
 *       spinning disks; NVMe mostly absorbs the difference.</li>
 *   <li>Entry count is cached in an {@link AtomicLong} rather than walked
 *       on every call.</li>
 * </ul>
 */
public final class FilesystemStorage implements SectionStore {
    private static final String SECTIONS_DIR = "sections";
    private static final String MESHES_DIR = "meshes";
    private static final String FILE_EXT = ".bin";

    private final Path root;
    private final AtomicLong sectionCount = new AtomicLong();

    public FilesystemStorage(Path root) {
        this.root = root;
        try {
            Files.createDirectories(root.resolve(SECTIONS_DIR));
            Files.createDirectories(root.resolve(MESHES_DIR));
            sectionCount.set(countExistingSections());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to initialise filesystem storage at " + root, e);
        }
        FarsightClient.LOGGER.info("FilesystemStorage opened at {} (initial sections={})",
            root, sectionCount.get());
    }

    @Override public void put(SectionKey k, byte[] v)      { write(SECTIONS_DIR, k, v, true); }
    @Override public byte[] get(SectionKey k)              { return read(SECTIONS_DIR, k); }
    @Override public boolean delete(SectionKey k)          { return remove(SECTIONS_DIR, k, true); }

    @Override public void putMesh(SectionKey k, byte[] v)  { write(MESHES_DIR, k, v, false); }
    @Override public byte[] getMesh(SectionKey k)          { return read(MESHES_DIR, k); }
    @Override public boolean deleteMesh(SectionKey k)      { return remove(MESHES_DIR, k, false); }

    @Override public long approximateEntryCount() { return sectionCount.get(); }
    @Override public void sync(boolean force)     { /* writes are atomic-rename; nothing to flush */ }
    @Override public void close()                 { /* no handles to release */ }

    private void write(String dir, SectionKey k, byte[] v, boolean isSection) {
        Path target = root.resolve(dir).resolve(fileName(k));
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            boolean existed = Files.exists(target);
            Files.write(tmp, v);
            Files.move(tmp, target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
            if (isSection && !existed) sectionCount.incrementAndGet();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] read(String dir, SectionKey k) {
        Path p = root.resolve(dir).resolve(fileName(k));
        if (!Files.exists(p)) return null;
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean remove(String dir, SectionKey k, boolean isSection) {
        Path p = root.resolve(dir).resolve(fileName(k));
        try {
            boolean removed = Files.deleteIfExists(p);
            if (isSection && removed) sectionCount.decrementAndGet();
            return removed;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String fileName(SectionKey k) {
        return k.dim() + "_" + k.sx() + "_" + k.sy() + "_" + k.sz() + "_" + k.level() + FILE_EXT;
    }

    private long countExistingSections() throws IOException {
        Path dir = root.resolve(SECTIONS_DIR);
        if (!Files.isDirectory(dir)) return 0L;
        long n = 0L;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + FILE_EXT)) {
            for (Path ignored : stream) n++;
        }
        return n;
    }
}
