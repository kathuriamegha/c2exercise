package com.c2exercise.specrag.ingest;

/** AC-1 response body. Names the backends so an eval run records what produced the index. */
public record IngestResult(
        int filesIngested,
        int chunksCreated,
        String chunker,
        String embeddingBackend,
        String storeBackend,
        long elapsedMs) {
}
