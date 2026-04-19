package me.xssmusashi.farsight.core.storage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 17-byte LMDB key:
 * <pre>
 *   i32 dim
 *   i32 sx
 *   i32 sy
 *   i32 sz
 *   i8  level
 * </pre>
 * <p>Encoded as little-endian. Section coordinates are in <em>section-space</em>
 * at the given LoD level: at level 0 each section covers 32 native voxels, at
 * level <i>L</i> it covers 32 * 2<sup>L</sup> native voxels.</p>
 */
public record SectionKey(int dim, int sx, int sy, int sz, int level) {
    public static final int BYTES = 17;

    public void writeTo(ByteBuffer buf) {
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(dim).putInt(sx).putInt(sy).putInt(sz).put((byte) level);
    }

    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(BYTES);
        writeTo(buf);
        return buf.array();
    }

    public static SectionKey read(ByteBuffer buf) {
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return new SectionKey(buf.getInt(), buf.getInt(), buf.getInt(), buf.getInt(), buf.get() & 0xFF);
    }

    public static SectionKey fromBytes(byte[] bytes) {
        return read(ByteBuffer.wrap(bytes));
    }
}
