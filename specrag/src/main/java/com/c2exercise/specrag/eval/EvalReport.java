package com.c2exercise.specrag.eval;

import java.util.List;

/**
 * AC-19 / AC-20. Aggregate scores plus every per-question result, so a bad average can be traced
 * to the question that caused it rather than merely observed.
 */
public record EvalReport(
        Config config,
        Summary summary,
        List<QuestionResult> results) {

    /** What produced these numbers. A score without this is not reproducible. */
    public record Config(
            String chunker,
            int maxTokens,
            int overlapTokens,
            int topK,
            String retrievalMode,
            double minScore,
            int contextMaxTokens,
            String embeddingBackend,
            String storeBackend,
            String generator,
            int chunksIndexed) {
    }

    public record Summary(
            int questions,
            double retrievalPrecisionAtK,
            double retrievalRecallAtK,
            double meanReciprocalRank,
            double answerAccuracy,
            int answered,
            int noRelevantContext,
            int contextTruncated,
            long totalElapsedMs,
            long meanQueryMs) {
    }

    /**
     * @param firstRelevantRank 1-based rank of the first correctly-sourced chunk; 0 when none
     * @param answerCorrect status is {@code answered}, at least one expected keyword appears, and
     *                      at least one citation points at an expected source
     */
    public record QuestionResult(
            String id,
            String question,
            String status,
            String answer,
            List<String> citedSources,
            List<String> retrievedSources,
            double precisionAtK,
            double recallAtK,
            int firstRelevantRank,
            List<String> matchedKeywords,
            List<String> missingKeywords,
            boolean answerCorrect,
            boolean contextTruncated,
            long elapsedMs) {
    }
}
