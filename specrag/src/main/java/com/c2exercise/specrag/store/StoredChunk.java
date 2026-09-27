package com.c2exercise.specrag.store;

import com.c2exercise.specrag.domain.Chunk;

/** A chunk together with its embedding, as held by a {@link VectorStore}. */
public record StoredChunk(Chunk chunk, float[] embedding) {
}
