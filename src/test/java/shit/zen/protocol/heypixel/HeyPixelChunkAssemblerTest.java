package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class HeyPixelChunkAssemblerTest {
    @Test
    void mergesZeroBasedChunksInIndexOrderAndRemovesCompletedTransfer() {
        HeyPixelChunkAssembler assembler = new HeyPixelChunkAssembler(8, 32);
        assertTrue(assembler.accept(new S2CPacketDecoders.ResourceBlobFragment(
            "transfer", "meta", 1, 2, new byte[]{3, 4})).isEmpty());

        HeyPixelChunkAssembler.CompletedResourceBlob completed = assembler.accept(
            new S2CPacketDecoders.ResourceBlobFragment(
                "transfer", "meta", 0, 2, new byte[]{1, 2})).orElseThrow();
        assertArrayEquals(new byte[]{1, 2, 3, 4}, completed.bytes());
        assertEquals("transfer", completed.resourceName());
        assertEquals("meta", completed.hash());
        assertEquals("transfer", completed.field00());
        byte[] returned = completed.bytes();
        returned[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3, 4}, completed.bytes());

        assertTrue(assembler.accept(new S2CPacketDecoders.ResourceBlobFragment(
            "transfer", "next", 0, 2, new byte[]{5})).isEmpty());
    }

    @Test
    void rejectsInvalidOrInconsistentChunkShapes() {
        HeyPixelChunkAssembler assembler = new HeyPixelChunkAssembler(2, 4);
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(
            new S2CPacketDecoders.ResourceIndexFragment("a", "b", 1, 2, 2, new byte[]{1})));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(
            new S2CPacketDecoders.ResourceIndexFragment("a", "b", 1, 0, 3, new byte[]{1})));

        assembler.accept(new S2CPacketDecoders.ResourceIndexFragment(
            "a", "b", 1, 0, 2, new byte[]{1, 2, 3}));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(
            new S2CPacketDecoders.ResourceIndexFragment("a", "b", 1, 1, 2, new byte[]{4, 5})));
    }

    @Test
    void rejectsTransferMetadataDriftAndConflictingDuplicateChunks() {
        HeyPixelChunkAssembler assembler = new HeyPixelChunkAssembler(8, 64, 4, 256);
        assembler.accept(new S2CPacketDecoders.ResourceBlobFragment(
            "resource", "meta-a", 0, 2, new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(
            new S2CPacketDecoders.ResourceBlobFragment(
                "resource", "meta-b", 1, 2, new byte[]{2})));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(
            new S2CPacketDecoders.ResourceBlobFragment(
                "resource", "meta-a", 0, 2, new byte[]{9})));

        assertTrue(assembler.accept(new S2CPacketDecoders.ResourceBlobFragment(
            "resource", "meta-a", 0, 2, new byte[]{1})).isEmpty());
        HeyPixelChunkAssembler.CompletedResourceBlob resource = assembler.accept(
            new S2CPacketDecoders.ResourceBlobFragment(
                "resource", "meta-a", 1, 2, new byte[]{2})).orElseThrow();
        assertEquals("meta-a", resource.field01());
        assertEquals("resource", resource.resourceName());
        assertEquals("meta-a", resource.hash());
        assertArrayEquals(new byte[]{1, 2}, resource.bytes());

        assembler.accept(new S2CPacketDecoders.ResourceIndexFragment(
            "data", "meta", 1, 0, 2, new byte[]{3}));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(
            new S2CPacketDecoders.ResourceIndexFragment(
                "data", "meta", 0, 1, 2, new byte[]{4})));
        HeyPixelChunkAssembler.CompletedResourceIndex data = assembler.accept(
            new S2CPacketDecoders.ResourceIndexFragment(
                "data", "meta", 1, 1, 2, new byte[]{4})).orElseThrow();
        assertEquals("meta", data.cacheHash());
        assertEquals(1, data.mode());
    }

    @Test
    void boundsActiveTransfersAndAggregateInFlightBytesBeforeStoringChunks() {
        HeyPixelChunkAssembler assembler = new HeyPixelChunkAssembler(8, 8, 3, 6);
        assembler.accept(new S2CPacketDecoders.ResourceBlobFragment(
            "resource-a", "meta", 0, 2, new byte[]{1, 2}));
        assembler.accept(new S2CPacketDecoders.ResourceIndexFragment(
            "data-b", "meta", 1, 0, 2, new byte[]{3, 4}));
        assembler.accept(new S2CPacketDecoders.ResourceBlobFragment(
            "resource-c", "meta", 0, 3, new byte[]{5}));

        assertThrows(IllegalArgumentException.class, () -> assembler.accept(
            new S2CPacketDecoders.ResourceBlobFragment(
                "resource-d", "meta", 0, 2, new byte[]{6})));

        assertThrows(IllegalArgumentException.class, () -> assembler.accept(
            new S2CPacketDecoders.ResourceBlobFragment(
                "resource-c", "meta", 1, 3, new byte[]{7, 8})));

        assembler.reset();
        assertTrue(assembler.accept(new S2CPacketDecoders.ResourceBlobFragment(
            "resource-c", "meta", 0, 2, new byte[]{6})).isEmpty());
    }

    @Test
    void expiresOrExplicitlyAbortsIncompleteTransfersWithoutAFullReset() {
        AtomicLong now = new AtomicLong();
        HeyPixelChunkAssembler assembler = new HeyPixelChunkAssembler(
            8, 64, 1, 64, 10, now::get);
        assembler.accept(new S2CPacketDecoders.ResourceBlobFragment(
            "stale", "meta", 0, 2, new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(
            new S2CPacketDecoders.ResourceBlobFragment(
                "blocked", "meta", 0, 2, new byte[]{2})));

        now.set(10);
        assertTrue(assembler.accept(new S2CPacketDecoders.ResourceBlobFragment(
            "replacement", "meta", 0, 2, new byte[]{3})).isEmpty());
        assertTrue(assembler.abortResourceTransfer("replacement"));
        assertFalse(assembler.abortResourceTransfer("replacement"));
        assertTrue(assembler.accept(new S2CPacketDecoders.ResourceIndexFragment(
            "data", "meta", 1, 0, 2, new byte[]{4})).isEmpty());
        assertTrue(assembler.abortResourceIndexTransfer("data"));
    }

    @Test
    void reservesExactMergeAndCompletedRecordCopiesBeforeAcceptingFinalChunk() {
        HeyPixelChunkAssembler assembler = new HeyPixelChunkAssembler(8, 8, 2, 11);
        assembler.accept(new S2CPacketDecoders.ResourceBlobFragment(
            "resource", "meta", 0, 2, new byte[]{1, 2}));

        assertThrows(IllegalArgumentException.class, () -> assembler.accept(
            new S2CPacketDecoders.ResourceBlobFragment(
                "resource", "meta", 1, 2, new byte[]{3, 4})));

        HeyPixelChunkAssembler.CompletedResourceBlob completed = assembler.accept(
            new S2CPacketDecoders.ResourceBlobFragment(
                "resource", "meta", 1, 2, new byte[]{3})).orElseThrow();
        assertArrayEquals(new byte[]{1, 2, 3}, completed.bytes());
    }

    @Test
    void exposesId109CompletedJsonAsAnImmutableCompositeKeyView() {
        HeyPixelChunkAssembler assembler = new HeyPixelChunkAssembler(8, 1_024, 2, 1_024);
        byte[] json = "{\"group\":{\"name\":\"value\",\"count\":2},"
            .concat("\"flags\":{\"enabled\":true}}")
            .getBytes(StandardCharsets.UTF_8);
        int split = json.length / 2;

        assertTrue(assembler.accept(new S2CPacketDecoders.ResourceIndexFragment(
            "transfer", "meta", 1, 0, 2,
            java.util.Arrays.copyOfRange(json, 0, split))).isEmpty());
        HeyPixelChunkAssembler.CompletedResourceIndex completed = assembler.accept(
            new S2CPacketDecoders.ResourceIndexFragment(
                "transfer", "meta", 1, 1, 2,
                java.util.Arrays.copyOfRange(json, split, json.length))).orElseThrow();

        Map<HeyPixelChunkAssembler.JsonEntryKey, String> flattened =
            completed.flattenedJsonEntries().orElseThrow();
        assertEquals(Map.of(
            new HeyPixelChunkAssembler.JsonEntryKey("group", "name"), "\"value\"",
            new HeyPixelChunkAssembler.JsonEntryKey("group", "count"), "2",
            new HeyPixelChunkAssembler.JsonEntryKey("flags", "enabled"), "true"
        ), flattened);
        assertThrows(UnsupportedOperationException.class,
            () -> flattened.put(
                new HeyPixelChunkAssembler.JsonEntryKey("mutated", "key"), "value"));
    }

    @Test
    void keepsId109CompositeKeysCollisionFreeAndPreservesJsonValueTypes() {
        String json = """
            {"a:b":{"c":"first"},"a":{"b:c":"second","nil":null,
            "object":{"x":1},"array":[1,true]}}
            """;
        HeyPixelChunkAssembler assembler = new HeyPixelChunkAssembler(8, 1_024, 2, 4_096);
        HeyPixelChunkAssembler.CompletedResourceIndex completed = assembler.accept(
            new S2CPacketDecoders.ResourceIndexFragment(
                "transfer", "meta", 1, 0, 1, json.getBytes(StandardCharsets.UTF_8)))
            .orElseThrow();

        Map<HeyPixelChunkAssembler.JsonEntryKey, String> flattened =
            completed.flattenedJsonEntries().orElseThrow();
        assertEquals("\"first\"", flattened.get(
            new HeyPixelChunkAssembler.JsonEntryKey("a:b", "c")));
        assertEquals("\"second\"", flattened.get(
            new HeyPixelChunkAssembler.JsonEntryKey("a", "b:c")));
        assertEquals("null", flattened.get(
            new HeyPixelChunkAssembler.JsonEntryKey("a", "nil")));
        assertEquals("{\"x\":1}", flattened.get(
            new HeyPixelChunkAssembler.JsonEntryKey("a", "object")));
        assertEquals("[1,true]", flattened.get(
            new HeyPixelChunkAssembler.JsonEntryKey("a", "array")));
    }

    @Test
    void keepsId109ZeroModeAsAnEarlyReturnWithoutParsingBytes() {
        HeyPixelChunkAssembler assembler = new HeyPixelChunkAssembler(8, 64, 2, 64);
        HeyPixelChunkAssembler.CompletedResourceIndex completed = assembler.accept(
            new S2CPacketDecoders.ResourceIndexFragment(
                "transfer", "meta", 0, 0, 1, new byte[]{(byte) 0xff})).orElseThrow();

        assertFalse(completed.flattenedJsonEntries().isPresent());
    }

    @Test
    void preservesTheLegacyChunkedDataAdapterWithoutChangingWirePositions() {
        S2CPacketDecoders.ChunkedDataFragment legacy =
            new S2CPacketDecoders.ChunkedDataFragment(
                "transfer", "cache", 1, 0, 1, "{}".getBytes(StandardCharsets.UTF_8));
        S2CPacketDecoders.ResourceIndexFragment canonical = legacy.toResourceIndex();
        assertEquals("transfer", canonical.indexName());
        assertEquals("transfer", canonical.transferKey());
        assertEquals("cache", canonical.cacheHash());
        assertEquals(1, canonical.mode());

        HeyPixelChunkAssembler.CompletedChunkedData completed =
            new HeyPixelChunkAssembler(8, 64, 2, 256).accept(legacy).orElseThrow();
        assertEquals("cache", completed.field01());
        assertEquals(1, completed.toResourceIndex().mode());
    }
}
