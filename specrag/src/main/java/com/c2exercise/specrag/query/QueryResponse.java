package com.c2exercise.specrag.query;

import com.c2exercise.specrag.retrieve.Citation;

import java.util.List;

/**
 * The AC-12 response body.
 *
 * <p>Everything the caller would need to audit the answer is on the wire: what was retrieved and
 * at what score, whether context was dropped, and whether the result came from cache.
 *
 * @param status           mirrors {@link com.c2exercise.specrag.answer.Answer#status()}
 * @param contextTruncated AC-18
 * @param retrievalMode    TASK-009: which ordering produced {@code retrieved}
 * @param cached           NFR-5: true when this was served from the query cache
 */
public record QueryResponse(
        String question,
        String status,
        String answer,
        List<Citation> citations,
        List<RetrievedChunk> retrieved,
        int contextTokens,
        boolean contextTruncated,
        String generator,
        String embeddingBackend,
        String storeBackend,
        String retrievalMode,
        boolean cached,
        long elapsedMs) {

    /** What retrieval surfaced, including chunks that the context budget later dropped. */
    public record RetrievedChunk(
            String chunkId,
            String sourcePath,
            String section,
            String sourceType,
            int tokenCount,
            double score,
            boolean inContext) {
    }

    public QueryResponse asCached(long elapsedMs) {
        return new QueryResponse(question, status, answer, citations, retrieved, contextTokens,
                contextTruncated, generator, embeddingBackend, storeBackend, retrievalMode, true,
                elapsedMs);
    }
}
