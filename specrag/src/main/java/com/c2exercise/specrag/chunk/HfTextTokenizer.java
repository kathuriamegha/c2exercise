package com.c2exercise.specrag.chunk;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@link TextTokenizer} backed by the tokenizer that ships inside the embedding model directory.
 *
 * <p>Loaded from the already-downloaded model path rather than the Hugging Face hub, so it works
 * offline and needs no second trusted host (see Drift #1).
 *
 * <p>Truncation and padding are explicitly disabled. The shipped {@code tokenizer.json} sets
 * {@code truncation.max_length = 128}, and a tokenizer that honours it reports 128 for a
 * thousand-token file — which is not a count, it is a cap. Chunkers budget against these numbers,
 * so a capped count silently defeats the budget (Drift #2).
 */
public class HfTextTokenizer implements TextTokenizer, AutoCloseable {

    /** Every sequence the encoder sees is wrapped in [CLS] … [SEP]. */
    private static final int SPECIAL_TOKENS = 2;
    private static final int FALLBACK_MAX_SEQUENCE_TOKENS = 128;

    private final HuggingFaceTokenizer tokenizer;
    private final int maxSequenceTokens;

    public HfTextTokenizer(Path modelDir) {
        Path tokenizerJson = modelDir.resolve("tokenizer.json");
        if (!Files.isRegularFile(tokenizerJson)) {
            throw new IllegalStateException("tokenizer.json not found under " + modelDir);
        }
        this.maxSequenceTokens = readConfiguredMaxLength(tokenizerJson);
        try {
            this.tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(tokenizerJson)
                    // Chunking budgets must not be inflated by [CLS]/[SEP]; the model adds them
                    // itself at embed time.
                    .optAddSpecialTokens(false)
                    // Count the text, not the prefix of it the model would keep (Drift #2).
                    .optTruncation(false)
                    .optPadding(false)
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load tokenizer from " + tokenizerJson, e);
        }
    }

    /**
     * The encoder's sequence limit, as the model itself declares it. Text beyond this point is
     * not merely compressed — it is absent from the vector, so it is the hard ceiling on any
     * chunk budget.
     */
    public int maxSequenceTokens() {
        return maxSequenceTokens;
    }

    /** Largest usable chunk budget: the sequence limit less the special tokens. */
    public int maxContentTokens() {
        return maxSequenceTokens - SPECIAL_TOKENS;
    }

    private static int readConfiguredMaxLength(Path tokenizerJson) {
        try {
            JsonNode truncation = new ObjectMapper().readTree(tokenizerJson.toFile()).path("truncation");
            JsonNode maxLength = truncation.path("max_length");
            return maxLength.isNumber() ? maxLength.asInt() : FALLBACK_MAX_SEQUENCE_TOKENS;
        } catch (RuntimeException e) {
            // A missing or unreadable setting is not fatal; the conservative default still bounds
            // the budget correctly for this model family.
            return FALLBACK_MAX_SEQUENCE_TOKENS;
        }
    }

    @Override
    public long[] encode(String text) {
        return tokenizer.encode(text).getIds();
    }

    @Override
    public String decode(long[] ids) {
        return tokenizer.decode(ids).trim();
    }

    @Override
    public void close() {
        tokenizer.close();
    }
}
