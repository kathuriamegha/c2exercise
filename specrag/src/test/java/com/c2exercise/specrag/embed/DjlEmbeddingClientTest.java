package com.c2exercise.specrag.embed;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-002 TASK-004. Covers AC-9 (dimensions + unit norm), AC-10 (determinism),
 * AC-11 (related text outscores unrelated) and NFR-2 (model loaded once per JVM).
 */
class DjlEmbeddingClientTest {

    private static final String MODEL_URL =
            "djl://ai.djl.huggingface.onnxruntime/sentence-transformers/all-MiniLM-L6-v2";
    private static final int DIMS = 384;

    private static DjlEmbeddingClient client;

    @BeforeAll
    static void setUp() {
        client = new DjlEmbeddingClient(MODEL_URL, DIMS);
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void embed_returnsUnitNormVectorOfExpectedDimension() {
        float[] v = client.embed("POST /api/auth/login returns a JWT");

        assertEquals(DIMS, v.length, "AC-9: vector length");
        assertEquals(1.0, Vectors.l2Norm(v), 1e-5, "AC-9: L2 norm is 1.0");
    }

    @Test
    void sameText_producesIdenticalVector() {
        String text = "The ownership guard returns 403 on cross-user access.";

        assertArrayEquals(client.embed(text), client.embed(text), "AC-10: determinism");
    }

    @Test
    void relatedText_outscoresUnrelated() {
        float[] question = client.embed("how do I log in");
        float[] related = client.embed("user authentication endpoint");
        float[] unrelated = client.embed("database index tuning");

        double relatedScore = Vectors.cosine(question, related);
        double unrelatedScore = Vectors.cosine(question, unrelated);

        assertTrue(relatedScore > unrelatedScore,
                "AC-11: related (" + relatedScore + ") must outscore unrelated (" + unrelatedScore + ")");
    }

    /**
     * NFR-2. The claim is that embedding reuses the loaded model — not that this JVM ever loads
     * exactly one, which stopped being true once other test classes began loading their own in
     * the same forked JVM. A delta is the honest form of the assertion: it holds however many
     * clients the surrounding suite has constructed.
     */
    @Test
    void embeddingNeverReloadsTheModel() {
        int before = DjlEmbeddingClient.loadCount();

        for (int i = 0; i < 10; i++) {
            client.embed("warm call " + i);
        }

        assertEquals(before, DjlEmbeddingClient.loadCount(),
                "NFR-2: embedding must reuse the loaded model, not reload it");
    }

    @Test
    void constructingAClientLoadsExactlyOneModel() {
        int before = DjlEmbeddingClient.loadCount();

        try (DjlEmbeddingClient extra = new DjlEmbeddingClient(MODEL_URL, 384)) {
            extra.embed("one load, no more");
            assertEquals(before + 1, DjlEmbeddingClient.loadCount(),
                    "NFR-2: one model load per client");
        }
    }

    @Test
    void backendId_namesTheModel() {
        assertEquals("djl:all-MiniLM-L6-v2", client.backendId());
    }
}
