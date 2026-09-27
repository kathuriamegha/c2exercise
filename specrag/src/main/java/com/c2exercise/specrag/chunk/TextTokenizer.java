package com.c2exercise.specrag.chunk;

/**
 * Token-level view of text, using the same vocabulary as the embedding model.
 *
 * <p>Chunkers budget in model tokens rather than words so that AC-6 ("every chunk is ≤
 * max-tokens") is exactly true rather than approximately true — a word-based budget can
 * overflow on code, where identifiers split into many subword tokens.
 */
public interface TextTokenizer {

    /** Token ids for {@code text}, without special tokens. */
    long[] encode(String text);

    /** Inverse of {@link #encode}, as faithfully as the vocabulary allows. */
    String decode(long[] ids);

    default int count(String text) {
        return encode(text).length;
    }
}
