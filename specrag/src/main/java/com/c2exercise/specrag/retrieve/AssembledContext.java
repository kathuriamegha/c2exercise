package com.c2exercise.specrag.retrieve;

import com.c2exercise.specrag.store.ScoredChunk;

import java.util.List;

/**
 * The context actually handed to the generator, plus the honest record of what was dropped.
 *
 * @param text       numbered passages, the string the generator sees
 * @param included   the chunks that survived the budget, best first
 * @param citations  one per included chunk, markers aligned with {@code text}
 * @param tokenCount tokens in {@code text}, never above the configured budget (AC-18)
 * @param truncated  AC-18: true when at least one retrieved chunk did not fit
 */
public record AssembledContext(
        String text,
        List<ScoredChunk> included,
        List<Citation> citations,
        int tokenCount,
        boolean truncated) {

    public boolean isEmpty() {
        return included.isEmpty();
    }
}
