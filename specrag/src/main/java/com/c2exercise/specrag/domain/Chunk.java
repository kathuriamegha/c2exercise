package com.c2exercise.specrag.domain;

import java.util.UUID;

/**
 * One retrievable unit of corpus text plus the metadata AC-7 requires.
 *
 * <p>Deliberately carries no embedding: chunking is a pure text operation and stays testable
 * without loading a model. The vector is held alongside the chunk by the store.
 *
 * @param id         stable identifier, referenced by citations (AC-17)
 * @param content    the chunk text
 * @param tokenCount model token count, ≤ configured max (AC-6)
 * @param sourcePath path relative to the configured allowed-root
 * @param sourceType markdown or java
 * @param section    nearest markdown heading, or Java type/member name; never null
 * @param ordinal    0-based position of this chunk within its source file
 */
public record Chunk(
        UUID id,
        String content,
        int tokenCount,
        String sourcePath,
        SourceType sourceType,
        String section,
        int ordinal) {

    public Chunk {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("chunk content must not be blank");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must be >= 0, was " + ordinal);
        }
        if (section == null) {
            section = "";
        }
    }

    public static Chunk of(String content, int tokenCount, String sourcePath,
                           SourceType sourceType, String section, int ordinal) {
        return new Chunk(UUID.randomUUID(), content, tokenCount, sourcePath,
                sourceType, section, ordinal);
    }

    /** Short provenance label used when rendering citations. */
    public String citationLabel() {
        return section.isBlank() ? sourcePath : sourcePath + " § " + section;
    }
}
