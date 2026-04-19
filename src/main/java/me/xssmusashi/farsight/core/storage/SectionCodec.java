package me.xssmusashi.farsight.core.storage;

import com.github.luben.zstd.Zstd;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

/**
 * Frames and compresses a serialized section payload with a tiny header:
 * <pre>
 *   u32 magic    = 0x46525356 ("FRSV")
 *   u8  version  = 1
 *   u32 crc32    of compressed payload
 *   ...         zstd-compressed bytes
 * </pre>
 */
public final class SectionCodec {
    public static final int MAGIC = 0x46525356; // 'FRSV' little-endian
    public static final byte VERSION = 1;
    public static final int HEADER_BYTES = 4 + 1 + 4;
    public static final int DEFAULT_LEVEL = 3;

    private SectionCodec() {}

    public static byte[] encode(byte[] payload) {
        return encode(payload, DEFAULT_LEVEL);
    }

    public static byte[] encode(byte[] payload, int level) {
        byte[] compressed = Zstd.compress(payload, level);
        CRC32 crc = new CRC32();
        crc.update(compressed);
        int checksum = (int) crc.getValue();

        ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES + compressed.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(MAGIC);
        buf.put(VERSION);
        buf.putInt(checksum);
        buf.put(compressed);
        return buf.array();
    }

    public static byte[] decode(byte[] framed) {
        if (framed.length < HEADER_BYTES) {
            throw new IllegalArgumentException("frame shorter than header");
        }
        ByteBuffer buf = ByteBuffer.wrap(framed).order(ByteOrder.LITTLE_ENDIAN);
        int magic = buf.getInt();
        if (magic != MAGIC) {
            throw new IllegalArgumentException("bad magic: 0x" + Integer.toHexString(magic));
        }
        byte version = buf.get();
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported version: " + version);
        }
        int expectedCrc = buf.getInt();
        byte[] compressed = new byte[framed.length - HEADER_BYTES];
        buf.get(compressed);

        CRC32 crc = new CRC32();
        crc.update(compressed);
        int actualCrc = (int) crc.getValue();
        if (actualCrc != expectedCrc) {
            throw new IllegalStateException("crc mismatch: expected 0x" + Integer.toHexString(expectedCrc)
                    + " got 0x" + Integer.toHexString(actualCrc));
        }

        long uncompressedSize = Zstd.getFrameContentSize(compressed);
        if (uncompressedSize < 0 || uncompressedSize > (long) Integer.MAX_VALUE) {
            throw new IllegalStateException("bad frame size: " + uncompressedSize);
        }
        byte[] out = new byte[(int) uncompressedSize];
        long decoded = Zstd.decompress(out, compressed);
        if (decoded != uncompressedSize) {
            throw new IllegalStateException("partial zstd decode: " + decoded + "/" + uncompressedSize);
        }
        return out;
    }
}
