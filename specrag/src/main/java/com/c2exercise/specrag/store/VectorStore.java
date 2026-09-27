package com.c2exercise.specrag.store;

import com.c2exercise.specrag.domain.Chunk;
import com.c2exercise.specrag.domain.SourceType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Holds embedded chunks and answers similarity queries.
 *
 * <p>Two implementations exist on purpose. SPEC-002 D-1 predicts that at this corpus size
 * (~100–150 vectors) an indexed PGVector store will <em>lose</em> to an exact in-memory scan,
 * because an ANN index adds overhead without pruning enough candidates. Keeping both behind one
 * interface is what makes that claim measurable instead of asserted.
 */
public interface VectorStore {

    /** Backend id used in eval reports, e.g. {@code in-memory} or {@code pgvector}. */
    String backendId();

    /**
     * Replaces the entire index. Ingest is a full rebuild (AC-5), so re-running an identical
     * ingest must not duplicate chunks.
     */
    void replaceAll(List<StoredChunk> chunks);

    int count();

    Optional<Chunk> byId(UUID id);

    /** Listing for inspection and citation verification (AC-17). */
    List<Chunk> list(int limit, SourceType sourceTypeFilter);

    /**
     * Top-K nearest chunks by cosine similarity, highest first.
     *
     * @param sourceTypeFilter optional metadata filter (AC-16); null means no filter
     * @return at most {@code min(topK, count())} results (AC-14)
     */
    List<ScoredChunk> search(float[] queryVector, int topK, SourceType sourceTypeFilter);
}
