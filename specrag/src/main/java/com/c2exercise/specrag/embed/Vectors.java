package com.c2exercise.specrag.embed;

/** Vector maths for retrieval. Kept explicit rather than pulled from a framework (SPEC-002 §2). */
public final class Vectors {

    private Vectors() {
    }

    /**
     * Cosine similarity. For L2-normalised vectors this is just the dot product,
     * but we divide anyway so the function is correct for un-normalised input too.
     */
    public static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "dimension mismatch: " + a.length + " vs " + b.length);
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** L2-normalise in place and return the same array. Satisfies AC-9 (unit norm). */
    public static float[] l2Normalize(float[] v) {
        double sum = 0;
        for (float x : v) {
            sum += (double) x * x;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) {
            return v;
        }
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    public static double l2Norm(float[] v) {
        double sum = 0;
        for (float x : v) {
            sum += (double) x * x;
        }
        return Math.sqrt(sum);
    }
}
