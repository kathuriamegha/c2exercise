package com.c2exercise.specrag.chunk;

import com.c2exercise.specrag.domain.Chunk;
import com.c2exercise.specrag.domain.SourceType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC-002 TASK-003. Covers AC-6 (token budget), AC-7 (section metadata), AC-8 (overlap). */
class ChunkerTest {

    private final TextTokenizer tokenizer = new WhitespaceTokenizer();

    private static final String MARKDOWN = """
            # SPEC-001

            ## Problem Statement

            Users need their own tasks. Every request must be scoped to the authenticated user.

            ## Acceptance Criteria

            AC-1 covers registration and returns a created user.
            AC-8 covers the ownership guard and returns forbidden on cross-user access.

            ## Drift Log

            Spring Boot 4.0 relocated the MockMvc autoconfiguration annotation.
            """;

    private static String words(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append("w").append(i).append(' ');
        }
        return sb.toString().strip();
    }

    @Nested
    class FixedSize {

        @Test
        void everyChunkIsWithinTokenBudget() {
            var chunker = new FixedSizeChunker(tokenizer, 20, 5);

            List<Chunk> chunks = chunker.chunk(words(200), "corpus.md", SourceType.MARKDOWN);

            assertFalse(chunks.isEmpty());
            chunks.forEach(c -> assertTrue(c.tokenCount() <= 20,
                    "AC-6: chunk " + c.ordinal() + " had " + c.tokenCount() + " tokens"));
        }

        @Test
        void consecutiveChunks_shareOverlapWindow() {
            int max = 20;
            int overlap = 5;
            var chunker = new FixedSizeChunker(tokenizer, max, overlap);

            List<Chunk> chunks = chunker.chunk(words(200), "corpus.md", SourceType.MARKDOWN);

            assertTrue(chunks.size() >= 2, "need at least two chunks to compare");
            for (int i = 0; i < chunks.size() - 1; i++) {
                long[] current = tokenizer.encode(chunks.get(i).content());
                long[] next = tokenizer.encode(chunks.get(i + 1).content());
                if (current.length < max) {
                    continue; // final short window has no successor overlap to check
                }
                long[] tail = Arrays.copyOfRange(current, current.length - overlap, current.length);
                long[] head = Arrays.copyOfRange(next, 0, overlap);

                assertArrayEqualsWithContext(tail, head, i);
            }
        }

        @Test
        void ordinalsAreSequentialFromZero() {
            var chunker = new FixedSizeChunker(tokenizer, 20, 5);

            List<Chunk> chunks = chunker.chunk(words(200), "corpus.md", SourceType.MARKDOWN);

            for (int i = 0; i < chunks.size(); i++) {
                assertEquals(i, chunks.get(i).ordinal(), "AC-7: ordinal must be 0-based and dense");
            }
        }

        private void assertArrayEqualsWithContext(long[] tail, long[] head, int index) {
            assertTrue(Arrays.equals(tail, head),
                    "AC-8: chunk " + index + " tail " + Arrays.toString(tail)
                            + " != chunk " + (index + 1) + " head " + Arrays.toString(head));
        }
    }

    @Nested
    class Paragraph {

        @Test
        void everyChunkIsWithinTokenBudget() {
            var chunker = new ParagraphChunker(tokenizer, 25);

            List<Chunk> chunks = chunker.chunk(MARKDOWN, "SPEC-001.md", SourceType.MARKDOWN);

            assertFalse(chunks.isEmpty());
            chunks.forEach(c -> assertTrue(c.tokenCount() <= 25,
                    "AC-6: '" + c.section() + "' had " + c.tokenCount() + " tokens"));
        }

        @Test
        void oversizedParagraphIsStillSplitToBudget() {
            var chunker = new ParagraphChunker(tokenizer, 10);

            List<Chunk> chunks = chunker.chunk(words(60), "big.md", SourceType.MARKDOWN);

            chunks.forEach(c -> assertTrue(c.tokenCount() <= 10,
                    "AC-6 is unconditional, even for a single over-budget paragraph"));
        }
    }

    @Nested
    class Recursive {

        @Test
        void everyChunkIsWithinTokenBudget() {
            var chunker = new RecursiveChunker(tokenizer, 25);

            List<Chunk> chunks = chunker.chunk(MARKDOWN, "SPEC-001.md", SourceType.MARKDOWN);

            assertFalse(chunks.isEmpty());
            chunks.forEach(c -> assertTrue(c.tokenCount() <= 25,
                    "AC-6: '" + c.section() + "' had " + c.tokenCount() + " tokens"));
        }

        @Test
        void chunksCarryTheirMarkdownSection() {
            var chunker = new RecursiveChunker(tokenizer, 25);

            List<Chunk> chunks = chunker.chunk(MARKDOWN, "SPEC-001.md", SourceType.MARKDOWN);

            assertTrue(chunks.stream().anyMatch(c -> c.section().contains("Drift Log")),
                    "AC-7: expected a chunk attributed to the Drift Log heading, got "
                            + chunks.stream().map(Chunk::section).toList());
        }

        @Test
        void javaSourceIsAttributedToTypeAndMember() {
            String java = """
                    package com.example;

                    public class TokenBlacklistService {

                        public void revoke(String token) {
                            blacklisted.add(token);
                        }
                    }
                    """;
            var chunker = new RecursiveChunker(tokenizer, 200);

            List<Chunk> chunks = chunker.chunk(java, "TokenBlacklistService.java", SourceType.JAVA);

            assertTrue(chunks.stream().anyMatch(c -> c.section().startsWith("TokenBlacklistService")),
                    "AC-7: expected Java type attribution, got "
                            + chunks.stream().map(Chunk::section).toList());
        }
    }
}
