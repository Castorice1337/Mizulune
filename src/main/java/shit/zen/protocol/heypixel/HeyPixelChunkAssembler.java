package shit.zen.protocol.heypixel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** In-memory reconstruction of the shared ID108/109 zero-based chunk protocol. */
public final class HeyPixelChunkAssembler {
    private static final int DEFAULT_MAX_CHUNKS = 16_384;
    private static final int DEFAULT_MAX_ASSEMBLED_BYTES = 256 * 1024 * 1024;
    private static final int DEFAULT_MAX_ACTIVE_TRANSFERS = 128;
    private static final long DEFAULT_TRANSFER_TTL_NANOS = TimeUnit.MINUTES.toNanos(2);
    private static final int COMPLETION_COPY_COUNT = 2;

    private final int maxChunks;
    private final int maxAssembledBytes;
    private final int maxActiveTransfers;
    private final long maxInFlightBytes;
    private final long transferTtlNanos;
    private final LongSupplier nanoTime;
    private final Map<String, Transfer> resourceTransfers = new HashMap<>();
    private final Map<String, Transfer> resourceIndexTransfers = new HashMap<>();
    private long inFlightBytes;

    public HeyPixelChunkAssembler() {
        this(
            DEFAULT_MAX_CHUNKS,
            DEFAULT_MAX_ASSEMBLED_BYTES,
            DEFAULT_MAX_ACTIVE_TRANSFERS,
            DEFAULT_MAX_ASSEMBLED_BYTES,
            DEFAULT_TRANSFER_TTL_NANOS,
            System::nanoTime
        );
    }

    HeyPixelChunkAssembler(int maxChunks, int maxAssembledBytes) {
        this(
            maxChunks,
            maxAssembledBytes,
            DEFAULT_MAX_ACTIVE_TRANSFERS,
            maxAssembledBytes,
            DEFAULT_TRANSFER_TTL_NANOS,
            System::nanoTime
        );
    }

    HeyPixelChunkAssembler(
        int maxChunks,
        int maxAssembledBytes,
        int maxActiveTransfers,
        long maxInFlightBytes
    ) {
        this(
            maxChunks,
            maxAssembledBytes,
            maxActiveTransfers,
            maxInFlightBytes,
            DEFAULT_TRANSFER_TTL_NANOS,
            System::nanoTime
        );
    }

    HeyPixelChunkAssembler(
        int maxChunks,
        int maxAssembledBytes,
        int maxActiveTransfers,
        long maxInFlightBytes,
        long transferTtlNanos,
        LongSupplier nanoTime
    ) {
        if (maxChunks <= 0 || maxAssembledBytes <= 0
            || maxActiveTransfers <= 0 || maxInFlightBytes <= 0 || transferTtlNanos <= 0) {
            throw new IllegalArgumentException("chunk limits must be positive");
        }
        this.maxChunks = maxChunks;
        this.maxAssembledBytes = maxAssembledBytes;
        this.maxActiveTransfers = maxActiveTransfers;
        this.maxInFlightBytes = maxInFlightBytes;
        this.transferTtlNanos = transferTtlNanos;
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public synchronized Optional<CompletedResourceBlob> accept(
        S2CPacketDecoders.ResourceBlobFragment fragment
    ) {
        Objects.requireNonNull(fragment, "fragment");
        Optional<CompletedTransfer> merged = accept(
            resourceTransfers,
            fragment.resourceName(),
            new ResourceMetadata(fragment.hash()),
            fragment.chunkIndex(),
            fragment.chunkCount(),
            fragment.chunkBytes()
        );
        return merged.map(completed -> {
            ResourceMetadata metadata = (ResourceMetadata) completed.metadata();
            return new CompletedResourceBlob(
                fragment.resourceName(), metadata.hash(), completed.bytes());
        });
    }

    public synchronized Optional<CompletedResourceIndex> accept(
        S2CPacketDecoders.ResourceIndexFragment fragment
    ) {
        Objects.requireNonNull(fragment, "fragment");
        Optional<CompletedTransfer> merged = accept(
            resourceIndexTransfers,
            fragment.indexName(),
            new DataMetadata(fragment.cacheHash(), fragment.mode()),
            fragment.chunkIndex(),
            fragment.chunkCount(),
            fragment.chunkBytes()
        );
        return merged.map(completed -> {
            DataMetadata metadata = (DataMetadata) completed.metadata();
            return new CompletedResourceIndex(
                fragment.indexName(), metadata.cacheHash(), metadata.mode(), completed.bytes());
        });
    }

    /** @deprecated Use {@link #accept(S2CPacketDecoders.ResourceIndexFragment)}. */
    @Deprecated
    public synchronized Optional<CompletedChunkedData> accept(
        S2CPacketDecoders.ChunkedDataFragment fragment
    ) {
        Objects.requireNonNull(fragment, "fragment");
        return accept(fragment.toResourceIndex()).map(CompletedChunkedData::fromResourceIndex);
    }

    public synchronized boolean abortResourceTransfer(String key) {
        return removeTransfer(resourceTransfers, Objects.requireNonNull(key, "key"));
    }

    public synchronized boolean abortResourceIndexTransfer(String key) {
        return removeTransfer(resourceIndexTransfers, Objects.requireNonNull(key, "key"));
    }

    /** @deprecated Use {@link #abortResourceIndexTransfer(String)}. */
    @Deprecated
    public synchronized boolean abortDataTransfer(String key) {
        return abortResourceIndexTransfer(key);
    }

    public synchronized void reset() {
        resourceTransfers.clear();
        resourceIndexTransfers.clear();
        inFlightBytes = 0;
    }

    private Optional<CompletedTransfer> accept(
        Map<String, Transfer> transfers,
        String key,
        Object metadata,
        int chunkIndex,
        int chunkCount,
        byte[] chunkBytes
    ) {
        Objects.requireNonNull(key, "transfer key");
        Objects.requireNonNull(metadata, "transfer metadata");
        Objects.requireNonNull(chunkBytes, "chunkBytes");
        validateShape(chunkIndex, chunkCount);
        long now = nanoTime.getAsLong();
        evictExpiredTransfers(now);
        Transfer transfer = transfers.get(key);
        boolean newTransfer = transfer == null;
        if (newTransfer) {
            if (activeTransferCount() >= maxActiveTransfers) {
                throw new IllegalArgumentException(
                    "active chunk transfers exceed " + maxActiveTransfers);
            }
            transfer = new Transfer(chunkCount, metadata, now);
        }
        if (transfer.chunks.length != chunkCount) {
            throw new IllegalArgumentException("chunk count changed for transfer " + key);
        }
        if (!transfer.metadata().equals(metadata)) {
            throw new IllegalArgumentException("chunk metadata changed for transfer " + key);
        }
        if (transfer.hasChunk(chunkIndex)
            && !Arrays.equals(transfer.chunk(chunkIndex), chunkBytes)) {
            throw new IllegalArgumentException("chunk bytes changed for transfer " + key
                + " at index " + chunkIndex);
        }

        long delta = transfer.replacementDelta(chunkIndex, chunkBytes.length);
        if (transfer.totalBytes() + delta > maxAssembledBytes) {
            throw new IllegalArgumentException(
                "assembled payload exceeds " + maxAssembledBytes + " bytes");
        }
        if (inFlightBytes + delta > maxInFlightBytes) {
            throw new IllegalArgumentException(
                "in-flight chunk payloads exceed " + maxInFlightBytes + " bytes");
        }
        if (transfer.wouldComplete(chunkIndex)) {
            long completedBytes = transfer.totalBytes() + delta;
            long completionPeak = inFlightBytes + delta + completedBytes * COMPLETION_COPY_COUNT;
            if (completionPeak > maxInFlightBytes) {
                throw new IllegalArgumentException(
                    "chunk completion peak exceeds " + maxInFlightBytes + " bytes");
            }
        }

        if (newTransfer) transfers.put(key, transfer);
        transfer.put(chunkIndex, chunkBytes, now);
        inFlightBytes += delta;
        if (!transfer.complete()) return Optional.empty();

        byte[] merged = transfer.merge(maxAssembledBytes);
        transfers.remove(key);
        inFlightBytes -= transfer.totalBytes();
        return Optional.of(new CompletedTransfer(transfer.metadata(), merged));
    }

    private boolean removeTransfer(Map<String, Transfer> transfers, String key) {
        Transfer removed = transfers.remove(key);
        if (removed == null) return false;
        inFlightBytes -= removed.totalBytes();
        return true;
    }

    private void evictExpiredTransfers(long now) {
        evictExpiredTransfers(resourceTransfers, now);
        evictExpiredTransfers(resourceIndexTransfers, now);
    }

    private void evictExpiredTransfers(Map<String, Transfer> transfers, long now) {
        Iterator<Transfer> iterator = transfers.values().iterator();
        while (iterator.hasNext()) {
            Transfer transfer = iterator.next();
            if (now - transfer.lastSeenNanos() < transferTtlNanos) continue;
            inFlightBytes -= transfer.totalBytes();
            iterator.remove();
        }
    }

    private int activeTransferCount() {
        return resourceTransfers.size() + resourceIndexTransfers.size();
    }

    private void validateShape(int chunkIndex, int chunkCount) {
        if (chunkCount <= 0 || chunkCount > maxChunks) {
            throw new IllegalArgumentException("invalid chunk count: " + chunkCount);
        }
        if (chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException(
                "chunk index " + chunkIndex + " outside count " + chunkCount);
        }
    }

    private static final class Transfer {
        private final byte[][] chunks;
        private final Object metadata;
        private int received;
        private long totalBytes;
        private long lastSeenNanos;

        private Transfer(int chunkCount, Object metadata, long now) {
            chunks = new byte[chunkCount][];
            this.metadata = metadata;
            lastSeenNanos = now;
        }

        private void put(int index, byte[] bytes, long now) {
            long delta = replacementDelta(index, bytes.length);
            if (chunks[index] == null) received++;
            chunks[index] = bytes.clone();
            totalBytes += delta;
            lastSeenNanos = now;
        }

        private Object metadata() {
            return metadata;
        }

        private boolean hasChunk(int index) {
            return chunks[index] != null;
        }

        private byte[] chunk(int index) {
            return chunks[index];
        }

        private long lastSeenNanos() {
            return lastSeenNanos;
        }

        private long replacementDelta(int index, int newLength) {
            byte[] previous = chunks[index];
            return (long) newLength - (previous == null ? 0 : previous.length);
        }

        private long totalBytes() {
            return totalBytes;
        }

        private boolean complete() {
            return received == chunks.length;
        }

        private boolean wouldComplete(int index) {
            return received + (chunks[index] == null ? 1 : 0) == chunks.length;
        }

        private byte[] merge(int maxBytes) {
            long total = 0;
            for (byte[] chunk : chunks) {
                total += chunk.length;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("assembled payload exceeds " + maxBytes + " bytes");
                }
            }
            byte[] output = new byte[(int) total];
            int offset = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, output, offset, chunk.length);
                offset += chunk.length;
            }
            return output;
        }
    }

    private record ResourceMetadata(String hash) {
    }

    private record DataMetadata(String cacheHash, int mode) {
    }

    private record CompletedTransfer(Object metadata, byte[] bytes) {
    }

    public record CompletedResourceBlob(String resourceName, String hash, byte[] bytes) {
        public CompletedResourceBlob {
            Objects.requireNonNull(resourceName, "resourceName");
            Objects.requireNonNull(hash, "hash");
            bytes = bytes.clone();
        }

        /** @deprecated Use {@link #resourceName()}. */
        @Deprecated
        public String field00() {
            return resourceName;
        }

        /** @deprecated Use {@link #hash()}. */
        @Deprecated
        public String field01() {
            return hash;
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public record CompletedResourceIndex(
        String indexName,
        String cacheHash,
        int mode,
        byte[] bytes
    ) {
        public CompletedResourceIndex {
            Objects.requireNonNull(indexName, "indexName");
            Objects.requireNonNull(cacheHash, "cacheHash");
            bytes = bytes.clone();
        }

        /** @deprecated Use {@link #indexName()}. */
        @Deprecated
        public String transferKey() {
            return indexName;
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        /**
         * Returns the official ID109 two-level JSON shape as a side-effect-free composite-key view.
         * The official persistence path additionally transforms each key before writing Nitrite;
         * that opaque transform is deliberately not reproduced here.
         */
        public Optional<Map<JsonEntryKey, String>> flattenedJsonEntries() {
            if (mode == 0) return Optional.empty();
            if (bytes.length > S2CPacketDecoders.MAX_JSON_BYTES) {
                throw new IllegalArgumentException(
                    "ID109 completed JSON exceeds " + S2CPacketDecoders.MAX_JSON_BYTES + " bytes");
            }

            var document = JsonParser.parseString(S2CPacketDecoders.decodeUtf8Strict(109, bytes));
            if (!document.isJsonObject()) {
                throw new IllegalArgumentException("ID109 completed JSON root is not an object");
            }
            JsonObject root = document.getAsJsonObject();
            LinkedHashMap<JsonEntryKey, String> flattened = new LinkedHashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> outer : root.entrySet()) {
                if (!outer.getValue().isJsonObject()) {
                    throw new IllegalArgumentException(
                        "ID109 outer entry is not an object: " + outer.getKey());
                }
                JsonObject nested = outer.getValue().getAsJsonObject();
                for (Map.Entry<String, com.google.gson.JsonElement> inner : nested.entrySet()) {
                    flattened.put(
                        new JsonEntryKey(outer.getKey(), inner.getKey()),
                        inner.getValue().toString()
                    );
                }
            }
            return Optional.of(Collections.unmodifiableMap(flattened));
        }
    }

    /** @deprecated Use {@link CompletedResourceIndex}. */
    @Deprecated
    public record CompletedChunkedData(String field00, String field01, int field02, byte[] bytes) {
        public CompletedChunkedData {
            bytes = bytes.clone();
        }

        public static CompletedChunkedData fromResourceIndex(CompletedResourceIndex index) {
            return new CompletedChunkedData(
                index.indexName(), index.cacheHash(), index.mode(), index.bytes());
        }

        public CompletedResourceIndex toResourceIndex() {
            return new CompletedResourceIndex(field00, field01, field02, bytes);
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        public Optional<Map<JsonEntryKey, String>> flattenedJsonEntries() {
            return toResourceIndex().flattenedJsonEntries();
        }
    }

    /** Collision-free structural key for the official ID109 two-level JSON shape. */
    public record JsonEntryKey(String outerKey, String innerKey) {
        public JsonEntryKey {
            Objects.requireNonNull(outerKey, "outerKey");
            Objects.requireNonNull(innerKey, "innerKey");
        }
    }
}
