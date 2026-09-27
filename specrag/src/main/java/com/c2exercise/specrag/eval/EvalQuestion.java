package com.c2exercise.specrag.eval;

import java.util.List;

/**
 * One labelled question in the eval set.
 *
 * <p>The label is deliberately weak: a list of source paths that <em>should</em> be retrieved and
 * a few keywords the answer should contain. A stronger label (an exact expected answer) would be
 * unscorable against an extractive generator, and scoring what we cannot measure is worse than
 * measuring something narrow honestly.
 *
 * @param expectedSources substrings matched against a chunk's source path
 * @param expectedKeywords case-insensitive substrings expected in the answer text
 * @param sourceType optional filter to exercise AC-16 from the eval set
 */
public record EvalQuestion(
        String id,
        String question,
        List<String> expectedSources,
        List<String> expectedKeywords,
        String sourceType) {
}
