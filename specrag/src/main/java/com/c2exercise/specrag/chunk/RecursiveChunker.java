package com.c2exercise.specrag.chunk;

import com.c2exercise.specrag.domain.Chunk;
import com.c2exercise.specrag.domain.SourceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits on a hierarchy of separators, backing off only as far as necessary.
 *
 * <p>Tries the most semantically meaningful boundary first (a markdown heading, a blank line),
 * and only falls through to line and word boundaries for text that is still over budget. Adjacent
 * under-budget pieces are then merged back up, so the result is neither ragged nor split
 * mid-thought. This is the default strategy.
 */
public class RecursiveChunker implements Chunker {

    private static final String[] MARKDOWN_SEPARATORS = {"\n## ", "\n### ", "\n\n", "\n", " "};
    private static final String[] JAVA_SEPARATORS = {"\n\n", "\n    }", "\n", " "};

    private final TextTokenizer tokenizer;
    private final int maxTokens;

    public RecursiveChunker(TextTokenizer tokenizer, int maxTokens) {
        this.tokenizer = tokenizer;
        this.maxTokens = maxTokens;
    }

    @Override
    public String id() {
        return "recursive";
    }

    @Override
    public List<Chunk> chunk(String content, String sourcePath, SourceType sourceType) {
        String[] separators = sourceType == SourceType.JAVA ? JAVA_SEPARATORS : MARKDOWN_SEPARATORS;

        List<String> pieces = new ArrayList<>();
        split(content, separators, 0, pieces);
        List<String> merged = mergeUnderBudget(pieces);

        List<Chunk> out = new ArrayList<>(merged.size());
        SectionTracker tracker = new SectionTracker(sourceType);
        int ordinal = 0;
        for (String piece : merged) {
            for (String line : piece.split("\n")) {
                tracker.accept(line);
            }
            String text = piece.strip();
            if (text.isEmpty()) {
                continue;
            }
            out.add(Chunk.of(text, tokenizer.count(text), sourcePath,
                    sourceType, tracker.current(), ordinal++));
        }
        return out;
    }

    /** Recursively split {@code text} until each piece fits, or separators are exhausted. */
    private void split(String text, String[] separators, int depth, List<String> out) {
        if (text.isBlank()) {
            return;
        }
        if (tokenizer.count(text) <= maxTokens) {
            out.add(text);
            return;
        }
        if (depth >= separators.length) {
            // Nothing left to split on: fall back to hard token windows so AC-6 still holds.
            out.addAll(hardWindows(text));
            return;
        }

        String separator = separators[depth];
        String[] parts = text.split(java.util.regex.Pattern.quote(separator));
        if (parts.length == 1) {
            split(text, separators, depth + 1, out);
            return;
        }
        for (int i = 0; i < parts.length; i++) {
            // Restore the separator we split on, so headings and braces are not lost.
            String part = (i == 0 ? parts[i] : separator + parts[i]);
            split(part, separators, depth + 1, out);
        }
    }

    private List<String> hardWindows(String text) {
        long[] ids = tokenizer.encode(text);
        List<String> out = new ArrayList<>();
        for (int start = 0; start < ids.length; start += maxTokens) {
            int end = Math.min(start + maxTokens, ids.length);
            out.add(tokenizer.decode(java.util.Arrays.copyOfRange(ids, start, end)));
        }
        return out;
    }

    /** Greedily recombine consecutive pieces while they still fit, to avoid tiny chunks. */
    private List<String> mergeUnderBudget(List<String> pieces) {
        List<String> merged = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (String piece : pieces) {
            if (buffer.length() == 0) {
                buffer.append(piece);
                continue;
            }
            String candidate = buffer + "\n" + piece;
            if (tokenizer.count(candidate) <= maxTokens) {
                buffer.setLength(0);
                buffer.append(candidate);
            } else {
                merged.add(buffer.toString());
                buffer.setLength(0);
                buffer.append(piece);
            }
        }
        if (buffer.length() > 0) {
            merged.add(buffer.toString());
        }
        return merged;
    }
}
