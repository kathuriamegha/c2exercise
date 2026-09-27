package com.c2exercise.specrag.retrieve;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * How candidates are ordered before top-K is applied (TASK-009).
 *
 * <p>Selectable per request so that two modes can be measured against one another in a single
 * process, over one index, with the model already warm. A restart-only switch would make the
 * comparison cheaper to implement and much harder to believe.
 */
public enum RetrievalMode {

    /** Cosine similarity alone. The baseline every other mode is measured against. */
    SEMANTIC("semantic"),

    /** Cosine to recall, BM25 to re-rank, the two fused by reciprocal rank. */
    HYBRID("hybrid");

    private final String wire;

    RetrievalMode(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static Optional<RetrievalMode> fromWire(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalised = value.strip().toLowerCase(Locale.ROOT);
        for (RetrievalMode mode : values()) {
            if (mode.wire.equals(normalised)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    public static List<String> wireNames() {
        return List.of(SEMANTIC.wire, HYBRID.wire);
    }
}
