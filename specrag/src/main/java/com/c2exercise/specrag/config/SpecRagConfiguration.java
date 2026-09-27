package com.c2exercise.specrag.config;

import com.c2exercise.specrag.chunk.Chunker;
import com.c2exercise.specrag.chunk.FixedSizeChunker;
import com.c2exercise.specrag.chunk.HfTextTokenizer;
import com.c2exercise.specrag.chunk.ParagraphChunker;
import com.c2exercise.specrag.chunk.RecursiveChunker;
import com.c2exercise.specrag.chunk.TextTokenizer;
import com.c2exercise.specrag.embed.DjlEmbeddingClient;
import com.c2exercise.specrag.store.InMemoryVectorStore;
import com.c2exercise.specrag.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the pipeline. Ordering matters in one place only: the tokenizer is derived from the
 * embedding model's own directory, so the embedding client must exist first.
 */
@Configuration
@EnableConfigurationProperties(SpecRagProperties.class)
public class SpecRagConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SpecRagConfiguration.class);

    @Bean(destroyMethod = "close")
    public DjlEmbeddingClient embeddingClient(SpecRagProperties properties) {
        return new DjlEmbeddingClient(
                properties.getEmbedding().getModelUrl(),
                properties.getEmbedding().getDimensions());
    }

    @Bean(destroyMethod = "close")
    public TextTokenizer textTokenizer(DjlEmbeddingClient embeddingClient,
                                       SpecRagProperties properties) {
        HfTextTokenizer tokenizer = new HfTextTokenizer(embeddingClient.modelPath());

        // Drift #2. A chunk larger than the encoder's sequence limit is not a slightly worse
        // chunk — its tail is simply not in the vector, and retrieval silently degrades with no
        // error anywhere. Refuse to start rather than index a corpus half-blind.
        int budget = properties.getChunk().getMaxTokens();
        int ceiling = tokenizer.maxContentTokens();
        if (budget > ceiling) {
            throw new IllegalStateException(
                    "specrag.chunk.max-tokens is " + budget + ", but the embedding model encodes at "
                            + "most " + tokenizer.maxSequenceTokens() + " tokens per sequence ("
                            + ceiling + " after [CLS]/[SEP]). Text beyond that would be indexed but "
                            + "never embedded. Lower max-tokens to " + ceiling + " or below.");
        }
        log.info("Chunk budget {} tokens, encoder sequence limit {} tokens", budget,
                tokenizer.maxSequenceTokens());
        return tokenizer;
    }

    /** The default (SPEC-002 §9): structure-aware, falls back to hard windows only when needed. */
    @Bean
    public Chunker recursiveChunker(TextTokenizer tokenizer, SpecRagProperties properties) {
        return new RecursiveChunker(tokenizer, properties.getChunk().getMaxTokens());
    }

    @Bean
    public Chunker paragraphChunker(TextTokenizer tokenizer, SpecRagProperties properties) {
        return new ParagraphChunker(tokenizer, properties.getChunk().getMaxTokens());
    }

    /** Registered so the eval harness can compare against a deliberately naive control. */
    @Bean
    public Chunker fixedSizeChunker(TextTokenizer tokenizer, SpecRagProperties properties) {
        return new FixedSizeChunker(tokenizer,
                properties.getChunk().getMaxTokens(),
                properties.getChunk().getOverlapTokens());
    }

    @Bean
    public VectorStore vectorStore() {
        return new InMemoryVectorStore();
    }
}
