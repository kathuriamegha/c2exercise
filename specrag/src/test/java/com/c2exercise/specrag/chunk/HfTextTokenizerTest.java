package com.c2exercise.specrag.chunk;

import com.c2exercise.specrag.domain.Chunk;
import com.c2exercise.specrag.domain.SourceType;
import com.c2exercise.specrag.embed.DjlEmbeddingClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression cover for Drift #2.
 *
 * <p>{@code ChunkerTest} runs against {@link WhitespaceTokenizer}, which has no notion of
 * truncation and therefore cannot reproduce the defect that shipped: the real tokenizer honoured
 * {@code truncation.max_length = 128} from {@code tokenizer.json}, so every count saturated at 128
 * and the chunkers concluded that entire files fit inside a 256-token budget. These tests use the
 * real tokenizer precisely because that is where the double stopped being faithful.
 */
class HfTextTokenizerTest {

    private static final int BUDGET = 120;

    private static DjlEmbeddingClient embeddingClient;
    private static HfTextTokenizer tokenizer;

    @BeforeAll
    static void loadModel() {
        embeddingClient = new DjlEmbeddingClient(
                "djl://ai.djl.huggingface.onnxruntime/sentence-transformers/all-MiniLM-L6-v2", 384);
        tokenizer = new HfTextTokenizer(embeddingClient.modelPath());
    }

    @AfterAll
    static void close() {
        tokenizer.close();
        embeddingClient.close();
    }

    @Test
    @DisplayName("counts describe the whole text, not the prefix the encoder would keep")
    void longTextIsNotSilentlyTruncatedByTheCounter() {
        String longText = ("the quick brown fox jumps over the lazy dog. ").repeat(80);

        int count = tokenizer.count(longText);

        // The exact number depends on the vocabulary; what matters is that it is not capped.
        assertThat(count)
                .as("a count that stops at the sequence limit is a cap, not a count")
                .isGreaterThan(tokenizer.maxSequenceTokens() * 2);
    }

    @Test
    @DisplayName("the encoder's sequence limit is reported, and bounds any chunk budget")
    void sequenceLimitIsDiscoverable() {
        assertThat(tokenizer.maxSequenceTokens()).isEqualTo(128);
        assertThat(tokenizer.maxContentTokens()).isEqualTo(126);
        assertThat(BUDGET).isLessThanOrEqualTo(tokenizer.maxContentTokens());
    }

    @Test
    @DisplayName("a file far larger than the budget is split, not emitted whole (AC-6)")
    void realWorldFileIsSplitToBudget() {
        String markdown = """
                # Retrieval

                ## Chunking

                """ + ("Chunk budgets are counted in model tokens rather than words, because a "
                + "code identifier can split into several subword tokens and overflow a "
                + "word-based budget without warning. ").repeat(30);

        List<Chunk> chunks = new RecursiveChunker(tokenizer, BUDGET)
                .chunk(markdown, "docs/retrieval.md", SourceType.MARKDOWN);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> {
            assertThat(c.tokenCount()).isLessThanOrEqualTo(BUDGET);
            // The reported count must describe the content, or AC-6 is unverifiable.
            assertThat(c.tokenCount()).isEqualTo(tokenizer.count(c.content()));
        });
    }
}
