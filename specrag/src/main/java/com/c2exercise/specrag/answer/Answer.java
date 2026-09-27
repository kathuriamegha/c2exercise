package com.c2exercise.specrag.answer;

import com.c2exercise.specrag.retrieve.Citation;

import java.util.List;

/**
 * @param status      {@code answered} or {@code no_relevant_context} (D-4/AC-13)
 * @param text        the answer body; empty string when there is nothing to answer from
 * @param citations   AC-17: a subset of the assembled context's citations — the ones the answer
 *                    text actually references, so every marker in {@code text} resolves
 * @param generatorId which generator produced this, recorded for eval runs
 */
public record Answer(String status, String text, List<Citation> citations, String generatorId) {

    public static final String ANSWERED = "answered";
    public static final String NO_RELEVANT_CONTEXT = "no_relevant_context";

    public static Answer noRelevantContext(String generatorId) {
        return new Answer(NO_RELEVANT_CONTEXT, "", List.of(), generatorId);
    }
}
