package com.c2exercise.specrag.chunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test double: one token per whitespace-delimited word, with a stable round-tripping vocabulary.
 *
 * <p>Lets the chunker tests assert exact token budgets and overlap windows without loading the
 * 109 MB embedding model — the chunkers' logic is independent of which vocabulary is used.
 */
class WhitespaceTokenizer implements TextTokenizer {

    private final Map<String, Long> toId = new HashMap<>();
    private final List<String> toWord = new ArrayList<>();

    @Override
    public long[] encode(String text) {
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            return new long[0];
        }
        String[] words = trimmed.split("\\s+");
        long[] ids = new long[words.length];
        for (int i = 0; i < words.length; i++) {
            ids[i] = toId.computeIfAbsent(words[i], w -> {
                toWord.add(w);
                return (long) (toWord.size() - 1);
            });
        }
        return ids;
    }

    @Override
    public String decode(long[] ids) {
        StringBuilder sb = new StringBuilder();
        for (long id : ids) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(toWord.get((int) id));
        }
        return sb.toString();
    }
}
