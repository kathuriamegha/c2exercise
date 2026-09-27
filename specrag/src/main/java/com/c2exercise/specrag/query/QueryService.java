package com.c2exercise.specrag.query;

import com.c2exercise.specrag.answer.Answer;
import com.c2exercise.specrag.answer.AnswerGenerator;
import com.c2exercise.specrag.config.SpecRagProperties;
import com.c2exercise.specrag.domain.SourceType;
import com.c2exercise.specrag.embed.EmbeddingClient;
import com.c2exercise.specrag.retrieve.AssembledContext;
import com.c2exercise.specrag.retrieve.ContextAssembler;
import com.c2exercise.specrag.retrieve.RetrievalMode;
import com.c2exercise.specrag.retrieve.Retriever;
import com.c2exercise.specrag.store.ScoredChunk;
import com.c2exercise.specrag.store.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The end-to-end RAG flow: validate → retrieve → assemble → answer (AC-12..AC-18). */
@Service
public class QueryService {

    private final Retriever retriever;
    private final ContextAssembler assembler;
    private final AnswerGenerator generator;
    private final VectorStore store;
    private final EmbeddingClient embeddingClient;
    private final SpecRagProperties properties;
    private final QueryCache cache;

    public QueryService(Retriever retriever,
                        ContextAssembler assembler,
                        AnswerGenerator generator,
                        VectorStore store,
                        EmbeddingClient embeddingClient,
                        SpecRagProperties properties,
                        QueryCache cache) {
        this.retriever = retriever;
        this.assembler = assembler;
        this.generator = generator;
        this.store = store;
        this.embeddingClient = embeddingClient;
        this.properties = properties;
        this.cache = cache;
    }

    public QueryResponse query(QueryRequest request) {
        long start = System.nanoTime();

        if (request == null || request.question() == null || request.question().isBlank()) {
            throw QueryException.questionRequired();          // AC-15
        }
        SourceType filter = resolveFilter(request.sourceType()); // AC-16
        if (store.count() == 0) {
            throw QueryException.indexEmpty();
        }

        int topK = request.topK() == null || request.topK() <= 0
                ? properties.getRetrieval().getDefaultTopK()
                : request.topK();
        RetrievalMode mode = resolveMode(request.mode());   // TASK-009
        String cacheKey = QueryCache.key(request, topK, mode);

        Optional<QueryResponse> cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get().asCached(elapsedMs(start));
        }

        List<ScoredChunk> hits = retriever.retrieve(request.question(), topK, filter, mode);
        AssembledContext context = assembler.assemble(hits);
        Answer answer = generator.generate(request.question(), context);

        QueryResponse response = new QueryResponse(
                request.question(),
                answer.status(),
                answer.text(),
                answer.citations(),
                describe(hits, context),
                context.tokenCount(),
                context.truncated(),
                generator.id(),
                embeddingClient.backendId(),
                store.backendId(),
                mode.wire(),
                false,
                elapsedMs(start));

        cache.put(cacheKey, response);
        return response;
    }

    private static SourceType resolveFilter(String requested) {
        if (requested == null || requested.isBlank()) {
            return null;
        }
        return SourceType.fromWire(requested)
                .orElseThrow(() -> QueryException.unknownSourceType(requested, SourceType.wireNames()));
    }

    /** An unrecognised mode is refused, so a typo in a benchmark run cannot quietly measure the default. */
    private RetrievalMode resolveMode(String requested) {
        if (requested == null || requested.isBlank()) {
            return retriever.defaultMode();
        }
        return RetrievalMode.fromWire(requested)
                .orElseThrow(() -> QueryException.unknownRetrievalMode(requested, RetrievalMode.wireNames()));
    }

    /**
     * Reports every hit, flagging which ones the context budget kept. A caller comparing a thin
     * answer against {@code retrieved} can see whether the problem was retrieval or truncation.
     */
    private static List<QueryResponse.RetrievedChunk> describe(List<ScoredChunk> hits,
                                                               AssembledContext context) {
        Set<UUID> inContext = new HashSet<>();
        context.included().forEach(c -> inContext.add(c.chunk().id()));

        List<QueryResponse.RetrievedChunk> out = new ArrayList<>(hits.size());
        for (ScoredChunk hit : hits) {
            out.add(new QueryResponse.RetrievedChunk(
                    hit.chunk().id().toString(),
                    hit.chunk().sourcePath(),
                    hit.chunk().section(),
                    hit.chunk().sourceType().wire(),
                    hit.chunk().tokenCount(),
                    hit.score(),
                    inContext.contains(hit.chunk().id())));
        }
        return out;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
