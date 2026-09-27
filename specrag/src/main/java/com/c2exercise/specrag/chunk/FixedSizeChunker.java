package com.c2exercise.specrag.chunk;

import com.c2exercise.specrag.domain.Chunk;
import com.c2exercise.specrag.domain.SourceType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fixed-size token windows with a fixed overlap.
 *
 * <p>The baseline strategy: it ignores document structure entirely, so a chunk can begin
 * mid-sentence or mid-method. That is the point — it is the control against which
 * {@link ParagraphChunker} and {@link RecursiveChunker} are measured, and it is deliberately
 * <em>not</em> the default (SPEC-002 §9).
 *
 * <p>Overlap exists so a fact straddling a window boundary survives in at least one chunk.
 */
public class FixedSizeChunker implements Chunker {

    private final TextTokenizer tokenizer;
    private final int maxTokens;
    private final int overlapTokens;

    public FixedSizeChunker(TextTokenizer tokenizer, int maxTokens, int overlapTokens) {
        if (overlapTokens >= maxTokens) {
            throw new IllegalArgumentException(
                    "overlapTokens (" + overlapTokens + ") must be < maxTokens (" + maxTokens + ")");
        }
        this.tokenizer = tokenizer;
        this.maxTokens = maxTokens;
        this.overlapTokens = overlapTokens;
    }

    @Override
    public String id() {
        return "fixed";
    }

    @Override
    public List<Chunk> chunk(String content, String sourcePath, SourceType sourceType) {
        long[] ids = tokenizer.encode(content);
        List<Chunk> out = new ArrayList<>();
        if (ids.length == 0) {
            return out;
        }

        int stride = maxTokens - overlapTokens;
        int ordinal = 0;
        for (int start = 0; start < ids.length; start += stride) {
            int end = Math.min(start + maxTokens, ids.length);
            long[] window = Arrays.copyOfRange(ids, start, end);
            String text = tokenizer.decode(window);
            if (!text.isBlank()) {
                String section = SectionTracker.firstSectionIn(text, sourceType, "");
                out.add(Chunk.of(text, window.length, sourcePath, sourceType, section, ordinal++));
            }
            if (end == ids.length) {
                break;
            }
        }
        return out;
    }
}
