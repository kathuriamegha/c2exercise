package com.c2exercise.specrag.retrieve;

import com.c2exercise.specrag.config.SpecRagProperties;
import com.c2exercise.specrag.domain.SourceType;
import com.c2exercise.specrag.embed.EmbeddingClient;
import com.c2exercise.specrag.store.ScoredChunk;
import com.c2exercise.specrag.store.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TASK-008 / TASK-009. Embeds the question, scans the store, applies top-K and the relevance
 * floor — and, in hybrid mode, re-ranks what survives by fusing the cosine and BM25 orderings.
 *
 * <h2>Why hybrid re-ranks rather than recalls</h2>
 * A textbook hybrid retriever unions two independent candidate lists. This one does not: BM25
 * re-orders an over-fetched <em>semantic</em> pool, and a chunk the encoder never surfaced cannot
 * be rescued by a keyword match. That is a deliberate trade, and its price is real — a chunk
 * whose only link to the question is an exact identifier, sitting below the pool cut, stays lost.
 *
 * <p>What it buys is that one rule still governs the whole pipeline: nothing below the cosine
 * floor is ever returned. AC-13 is a claim about the corpus being relevant at all, and lexical
 * scores cannot express it — BM25 will happily rank a chunk first for sharing one rare token with
 * a question the corpus cannot answer. Keeping the floor as the sole gate keeps "no relevant
 * context" meaning the same thing in both modes, which is the precondition for comparing them.
 */
@Service
public class Retriever {

    private final EmbeddingClient embeddingClient;
    private final VectorStore store;
    private final LexicalIndex lexicalIndex;
    private final SpecRagProperties properties;

    public Retriever(EmbeddingClient embeddingClient, VectorStore store,
                     LexicalIndex lexicalIndex, SpecRagProperties properties) {
        this.embeddingClient = embeddingClient;
        this.store = store;
        this.lexicalIndex = lexicalIndex;
        this.properties = properties;
    }

    public List<ScoredChunk> retrieve(String question, Integer requestedTopK, SourceType filter) {
        return retrieve(question, requestedTopK, filter, defaultMode());
    }

    public RetrievalMode defaultMode() {
        return RetrievalMode.fromWire(properties.getRetrieval().getMode()).orElse(RetrievalMode.SEMANTIC);
    }

    public List<ScoredChunk> retrieve(String question, Integer requestedTopK, SourceType filter,
                                      RetrievalMode mode) {
        SpecRagProperties.Retrieval config = properties.getRetrieval();
        int topK = requestedTopK == null || requestedTopK <= 0 ? config.getDefaultTopK() : requestedTopK;

        // Semantic recall. Hybrid needs a pool wider than topK or fusion has nothing to reorder.
        int poolSize = mode == RetrievalMode.HYBRID
                ? Math.max(topK, topK * config.getCandidateMultiplier())
                : topK;

        float[] queryVector = embeddingClient.embed(question);
        List<ScoredChunk> pool = store.search(queryVector, poolSize, filter);

        // AC-13: everything below the floor is discarded here, so the generator is never handed
        // weak context it might present as an answer. Applied before fusion, in both modes, so
        // re-ranking can reorder the relevant set but never widen it.
        double floor = config.getMinScore();
        pool = pool.stream().filter(h -> h.score() >= floor).toList();

        if (mode == RetrievalMode.SEMANTIC || pool.size() <= 1) {
            return pool.subList(0, Math.min(topK, pool.size()));
        }
        return fuse(question, pool, topK, config.getRrfK());
    }

    /**
     * Reciprocal rank fusion: {@code score = 1/(k + semanticRank) + 1/(k + lexicalRank)}.
     *
     * <p>Rank-based rather than score-based on purpose. A cosine over unit vectors lives in a
     * narrow band around 0.3–0.7 while BM25 is unbounded and scales with corpus statistics, so any
     * weighted sum of the two raw scores is really a weighting of their units. Ranks are
     * comparable by construction, and {@code k} — 60 by convention — damps the top of each list
     * so a single first place cannot outvote agreement further down.
     */
    private List<ScoredChunk> fuse(String question, List<ScoredChunk> pool, int topK, int k) {
        List<String> queryTerms = LexicalTokens.of(question);

        Map<UUID, Integer> semanticRank = new HashMap<>();
        for (int i = 0; i < pool.size(); i++) {
            semanticRank.put(pool.get(i).chunk().id(), i + 1);
        }

        // A chunk with no query term in it has a BM25 score of exactly zero, and giving those a
        // shared last place is the honest reading: they are not ranked by lexical evidence, they
        // have none. Without this they would be ordered by tie-break noise and fusion would treat
        // that order as a signal.
        List<ScoredChunk> lexicalOrder = new ArrayList<>(pool);
        Map<UUID, Double> lexicalScore = new HashMap<>();
        for (ScoredChunk hit : pool) {
            lexicalScore.put(hit.chunk().id(), lexicalIndex.score(queryTerms, hit.chunk().id()));
        }
        lexicalOrder.sort(Comparator.comparingDouble(
                (ScoredChunk h) -> lexicalScore.getOrDefault(h.chunk().id(), 0.0)).reversed());

        int unmatchedRank = pool.size() + 1;
        Map<UUID, Integer> lexicalRank = new HashMap<>();
        for (int i = 0; i < lexicalOrder.size(); i++) {
            UUID id = lexicalOrder.get(i).chunk().id();
            lexicalRank.put(id, lexicalScore.getOrDefault(id, 0.0) > 0 ? i + 1 : unmatchedRank);
        }

        Map<UUID, Double> fused = new HashMap<>();
        for (ScoredChunk hit : pool) {
            UUID id = hit.chunk().id();
            fused.put(id, 1.0 / (k + semanticRank.get(id)) + 1.0 / (k + lexicalRank.get(id)));
        }

        List<ScoredChunk> ranked = new ArrayList<>(pool);
        // Cosine breaks ties, so fusion never degrades to an arbitrary order: where the two
        // rankings agree exactly, the result is the semantic ranking.
        ranked.sort(Comparator
                .comparingDouble((ScoredChunk h) -> fused.get(h.chunk().id())).reversed()
                .thenComparing(Comparator.comparingDouble(ScoredChunk::score).reversed()));

        return ranked.subList(0, Math.min(topK, ranked.size()));
    }
}
