package me.xssmusashi.farsight.core.storage;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MeshBlobTest {

    @Test
    void roundTripEmptyPayload() {
        MeshBlob in = new MeshBlob(1, 2, 3, 1.0f, 0, new byte[0]);
        MeshBlob out = MeshBlob.decode(in.encode());
        assertEquals(in.originX(), out.originX());
        assertEquals(in.originY(), out.originY());
        assertEquals(in.originZ(), out.originZ());
        assertEquals(in.quadCount(), out.quadCount());
        assertEquals(in.voxelScale(), out.voxelScale());
        assertEquals(0, out.vertexBytes().length);
    }

    @Test
    void roundTripPayload() {
        byte[] payload = new byte[1024];
        new Random(0xFEED).nextBytes(payload);
        MeshBlob in = new MeshBlob(-64, 16, 128, 4.0f, 16, payload);
        MeshBlob out = MeshBlob.decode(in.encode());
        assertArrayEquals(payload, out.vertexBytes());
        assertEquals(4.0f, out.voxelScale());
        assertEquals(16, out.quadCount());
    }

    @Test
    void rejectsTruncated() {
        byte[] bad = {1, 2};
        assertThrows(IllegalArgumentException.class, () -> MeshBlob.decode(bad));
    }
}
