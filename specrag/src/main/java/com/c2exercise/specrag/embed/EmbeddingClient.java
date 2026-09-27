package com.c2exercise.specrag.embed;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns text into a dense vector.
 *
 * <p>Deliberately an interface with more than one implementation: SPEC-002 D-3 records that the
 * cloud backend (Bedrock Titan) cannot be exercised on this machine, so the seam stays open
 * rather than the abstraction being dropped.
 */
public interface EmbeddingClient {

    /** Returns an L2-normalised vector of length {@link #dimensions()} (AC-9). */
    float[] embed(String text);

    /** Vector length produced by this backend. */
    int dimensions();

    /** Human-readable backend id, e.g. {@code djl:all-MiniLM-L6-v2}. Used in eval reports. */
    String backendId();

    default List<float[]> embedAll(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (String t : texts) {
            out.add(embed(t));
        }
        return out;
    }
}
