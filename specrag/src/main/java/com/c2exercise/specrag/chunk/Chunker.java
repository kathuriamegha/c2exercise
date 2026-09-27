package com.c2exercise.specrag.chunk;

import com.c2exercise.specrag.domain.Chunk;
import com.c2exercise.specrag.domain.SourceType;

import java.util.List;

/**
 * Splits a source file into retrievable chunks.
 *
 * <p>Three strategies exist so the trade-off is measurable rather than asserted: {@code fixed}
 * ignores document structure, {@code paragraph} respects blank-line boundaries, and
 * {@code recursive} backs off through a separator hierarchy. The eval harness (TASK-014) scores
 * them against the same labelled question set.
 */
public interface Chunker {

    /** Wire name accepted by {@code POST /api/ingest} (AC-4). */
    String id();

    /** Every returned chunk satisfies {@code tokenCount <= maxTokens} (AC-6). */
    List<Chunk> chunk(String content, String sourcePath, SourceType sourceType);
}
