package com.c2exercise.specrag.retrieve;

import com.c2exercise.specrag.domain.Chunk;
import com.c2exercise.specrag.ingest.IndexRebuiltEvent;
import com.c2exercise.specrag.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TASK-009. BM25 scoring over the indexed chunks, for the lexical half of hybrid retrieval.
 *
 * <p>An embedding is a lossy summary, and the loss falls hardest on exactly the tokens this corpus
 * is made of: {@code path_outside_allowed_root} and {@code no_relevant_context} are rare strings
 * that carry the whole meaning of a question, and a 384-dimensional average of a 120-token window
 * does not preserve them. BM25 does, because a rare term is precisely what it weights highest.
 * That is the argument for hybrid; whether it holds on this corpus is what the eval harness is
 * for, not something to assert here.
 *
 * <p>Statistics are corpus-wide and rebuilt on {@link IndexRebuiltEvent}, for the same reason the
 * query cache is dropped there: term frequencies computed against a previous index describe
 * documents that no longer exist.
 */
@Component
public class LexicalIndex {

    private static final Logger log = LoggerFactory.getLogger(LexicalIndex.class);

    /** Saturation and length-normalisation constants; the usual defaults, not tuned here. */
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final VectorStore store;

    private final Object lock = new Object();
    private Map<UUID, Map<String, Integer>> termFrequencies = Map.of();
    private Map<UUID, Integer> documentLengths = Map.of();
    private Map<String, Integer> documentFrequencies = Map.of();
    private double averageDocumentLength;
    private int documentCount;

    public LexicalIndex(VectorStore store) {
        this.store = store;
    }

    @EventListener
    public void onIndexRebuilt(IndexRebuiltEvent event) {
        rebuild();
    }

    /** Rebuilds from whatever the vector store currently holds. */
    public void rebuild() {
        List<Chunk> chunks = store.list(Integer.MAX_VALUE, null);

        Map<UUID, Map<String, Integer>> tf = new HashMap<>(chunks.size() * 2);
        Map<UUID, Integer> lengths = new HashMap<>(chunks.size() * 2);
        Map<String, Integer> df = new HashMap<>();
        long totalLength = 0;

        for (Chunk chunk : chunks) {
            List<String> tokens = LexicalTokens.of(chunk.content());
            Map<String, Integer> counts = new HashMap<>();
            for (String token : tokens) {
                counts.merge(token, 1, Integer::sum);
            }
            tf.put(chunk.id(), counts);
            lengths.put(chunk.id(), tokens.size());
            totalLength += tokens.size();
            for (String term : counts.keySet()) {
                df.merge(term, 1, Integer::sum);
            }
        }

        synchronized (lock) {
            this.termFrequencies = tf;
            this.documentLengths = lengths;
            this.documentFrequencies = df;
            this.documentCount = chunks.size();
            this.averageDocumentLength = chunks.isEmpty() ? 0 : (double) totalLength / chunks.size();
        }
        log.info("Lexical index rebuilt: {} chunks, {} distinct terms, mean length {} tokens",
                chunks.size(), df.size(), Math.round(averageDocumentLength));
    }

    public int size() {
        synchronized (lock) {
            return documentCount;
        }
    }

    /**
     * BM25 score of one chunk against the query. Zero means no query term occurs in the chunk,
     * which is a real answer and not a missing value: it is what lets fusion tell "this chunk has
     * no lexical support" apart from "this chunk ranked last".
     */
    public double score(List<String> queryTerms, UUID chunkId) {
        synchronized (lock) {
            Map<String, Integer> counts = termFrequencies.get(chunkId);
            if (counts == null || documentCount == 0) {
                return 0.0;
            }
            double length = documentLengths.getOrDefault(chunkId, 0);
            double norm = K1 * (1 - B + B * (averageDocumentLength == 0 ? 1 : length / averageDocumentLength));

            double score = 0.0;
            for (String term : queryTerms) {
                Integer tf = counts.get(term);
                if (tf == null) {
                    continue;
                }
                int df = documentFrequencies.getOrDefault(term, 0);
                double idf = Math.log(1 + (documentCount - df + 0.5) / (df + 0.5));
                score += idf * (tf * (K1 + 1)) / (tf + norm);
            }
            return score;
        }
    }
}
