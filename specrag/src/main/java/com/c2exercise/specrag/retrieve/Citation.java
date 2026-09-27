package com.c2exercise.specrag.retrieve;

import java.util.UUID;

/**
 * AC-17. A citation names a chunk that is actually in the assembled context — never a chunk that
 * was retrieved but then truncated away, and never a path the generator invented.
 */
public record Citation(
        int marker,
        UUID chunkId,
        String sourcePath,
        String section,
        double score) {
}
