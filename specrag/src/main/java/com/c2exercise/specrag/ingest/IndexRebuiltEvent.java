package com.c2exercise.specrag.ingest;

/**
 * Published after the store has been replaced. Lets caches invalidate without the ingest path
 * having to know that caching exists.
 */
public record IndexRebuiltEvent(int chunkCount, String chunker) {
}
