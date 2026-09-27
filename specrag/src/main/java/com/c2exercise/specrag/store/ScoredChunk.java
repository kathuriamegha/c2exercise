package com.c2exercise.specrag.store;

import com.c2exercise.specrag.domain.Chunk;

/** A retrieval hit: the chunk and its similarity to the query, in [-1.0, 1.0]. */
public record ScoredChunk(Chunk chunk, double score) implements Comparable<ScoredChunk> {

    /** Descending by score, so natural ordering is "best first". */
    @Override
    public int compareTo(ScoredChunk other) {
        return Double.compare(other.score, this.score);
    }
}
