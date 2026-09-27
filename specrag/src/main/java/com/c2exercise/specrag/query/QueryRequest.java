package com.c2exercise.specrag.query;

/**
 * @param question   required; blank is rejected rather than embedded (AC-15)
 * @param topK       optional override of {@code specrag.retrieval.default-top-k}
 * @param sourceType optional metadata filter, {@code "markdown"} or {@code "java"} (AC-16)
 * @param mode       optional override of {@code specrag.retrieval.mode}, {@code "semantic"} or
 *                   {@code "hybrid"} (TASK-009)
 */
public record QueryRequest(String question, Integer topK, String sourceType, String mode) {

    public QueryRequest(String question, Integer topK, String sourceType) {
        this(question, topK, sourceType, null);
    }
}
