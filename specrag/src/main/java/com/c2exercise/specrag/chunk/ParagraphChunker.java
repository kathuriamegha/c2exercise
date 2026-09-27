package com.c2exercise.specrag.chunk;

import com.c2exercise.specrag.domain.Chunk;
import com.c2exercise.specrag.domain.SourceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Packs whole paragraphs (blank-line separated blocks) into chunks up to the token budget.
 *
 * <p>Never splits a paragraph, so a chunk always reads as complete prose. The cost is variance:
 * a single paragraph longer than the budget is emitted oversized unless split, so this chunker
 * falls back to hard token windows for that one block.
 */
public class ParagraphChunker implements Chunker {

    private final TextTokenizer tokenizer;
    private final int maxTokens;
    private final FixedSizeChunker oversizeFallback;

    public ParagraphChunker(TextTokenizer tokenizer, int maxTokens) {
        this.tokenizer = tokenizer;
        this.maxTokens = maxTokens;
        // AC-6 is unconditional, so an over-budget paragraph still has to be cut somewhere.
        this.oversizeFallback = new FixedSizeChunker(tokenizer, maxTokens, 0);
    }

    @Override
    public String id() {
        return "paragraph";
    }

    @Override
    public List<Chunk> chunk(String content, String sourcePath, SourceType sourceType) {
        List<Chunk> out = new ArrayList<>();
        SectionTracker tracker = new SectionTracker(sourceType);

        StringBuilder buffer = new StringBuilder();
        int bufferTokens = 0;
        String bufferSection = "";
        int ordinal = 0;

        for (String block : content.split("\n\\s*\n")) {
            if (block.isBlank()) {
                continue;
            }
            for (String line : block.split("\n")) {
                tracker.accept(line);
            }
            String section = tracker.current();
            int blockTokens = tokenizer.count(block);

            if (blockTokens > maxTokens) {
                if (bufferTokens > 0) {
                    out.add(emit(buffer, bufferTokens, sourcePath, sourceType, bufferSection, ordinal++));
                    buffer.setLength(0);
                    bufferTokens = 0;
                }
                for (Chunk piece : oversizeFallback.chunk(block, sourcePath, sourceType)) {
                    out.add(new Chunk(piece.id(), piece.content(), piece.tokenCount(),
                            sourcePath, sourceType, section, ordinal++));
                }
                continue;
            }

            if (bufferTokens + blockTokens > maxTokens && bufferTokens > 0) {
                out.add(emit(buffer, bufferTokens, sourcePath, sourceType, bufferSection, ordinal++));
                buffer.setLength(0);
                bufferTokens = 0;
            }
            if (bufferTokens == 0) {
                bufferSection = section;
            }
            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(block.strip());
            bufferTokens += blockTokens;
        }

        if (bufferTokens > 0) {
            out.add(emit(buffer, bufferTokens, sourcePath, sourceType, bufferSection, ordinal));
        }
        return out;
    }

    /**
     * Re-counts tokens on the joined text rather than trusting the running sum of per-block
     * counts: joining with separators can tokenize differently, and AC-6 asserts on the
     * {@code tokenCount} we report, so that number has to be measured, not estimated.
     */
    private Chunk emit(StringBuilder buffer, int estimatedTokens, String sourcePath,
                       SourceType sourceType, String section, int ordinal) {
        String text = buffer.toString();
        return Chunk.of(text, tokenizer.count(text), sourcePath, sourceType, section, ordinal);
    }
}
