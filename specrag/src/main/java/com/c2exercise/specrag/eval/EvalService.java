package com.c2exercise.specrag.eval;

import com.c2exercise.specrag.answer.Answer;
import com.c2exercise.specrag.answer.AnswerGenerator;
import com.c2exercise.specrag.config.SpecRagProperties;
import com.c2exercise.specrag.embed.EmbeddingClient;
import com.c2exercise.specrag.query.QueryRequest;
import com.c2exercise.specrag.query.QueryResponse;
import com.c2exercise.specrag.query.QueryService;
import com.c2exercise.specrag.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * TASK-014. Runs the labelled question set against the live pipeline and scores it.
 *
 * <p>The harness exists to make tuning arguments settleable. Changing the chunker or top-K and
 * re-running gives two comparable numbers; without it, "this retrieval feels better" is the only
 * available claim.
 */
@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);
    private static final String QUESTIONS_RESOURCE = "eval/questions.json";

    private final QueryService queryService;
    private final VectorStore store;
    private final EmbeddingClient embeddingClient;
    private final AnswerGenerator generator;
    private final SpecRagProperties properties;
    private final ObjectMapper objectMapper;

    public EvalService(QueryService queryService,
                       VectorStore store,
                       EmbeddingClient embeddingClient,
                       AnswerGenerator generator,
                       SpecRagProperties properties,
                       ObjectMapper objectMapper) {
        this.queryService = queryService;
        this.store = store;
        this.embeddingClient = embeddingClient;
        this.generator = generator;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public EvalReport run(Integer topKOverride) {
        return run(topKOverride, null);
    }

    /**
     * @param modeOverride TASK-009: {@code semantic} or {@code hybrid}; null uses the configured
     *                     default. An unknown mode propagates the query service's 400 rather than
     *                     silently measuring the default under the wrong label.
     */
    public EvalReport run(Integer topKOverride, String modeOverride) {
        List<EvalQuestion> questions = loadQuestions();
        int topK = topKOverride == null || topKOverride <= 0
                ? properties.getRetrieval().getDefaultTopK()
                : topKOverride;

        String mode = modeOverride == null || modeOverride.isBlank()
                ? properties.getRetrieval().getMode()
                : modeOverride.strip();

        long start = System.currentTimeMillis();
        List<EvalReport.QuestionResult> results = new ArrayList<>(questions.size());
        for (EvalQuestion question : questions) {
            results.add(score(question, topK, mode));
        }
        long elapsed = System.currentTimeMillis() - start;

        log.info("Eval complete: {} questions in {} ms (mode={})", results.size(), elapsed, mode);
        return new EvalReport(config(topK, mode), summarise(results, elapsed), results);
    }

    List<EvalQuestion> loadQuestions() {
        try (InputStream in = new ClassPathResource(QUESTIONS_RESOURCE).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<EvalQuestion>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + QUESTIONS_RESOURCE, e);
        }
    }

    private EvalReport.QuestionResult score(EvalQuestion question, int topK, String mode) {
        QueryResponse response = queryService.query(
                new QueryRequest(question.question(), topK, question.sourceType(), mode));

        List<String> retrievedSources = response.retrieved().stream()
                .map(QueryResponse.RetrievedChunk::sourcePath).toList();
        List<String> citedSources = response.citations().stream()
                .map(c -> c.sourcePath()).toList();

        int relevantRetrieved = 0;
        int firstRelevantRank = 0;
        for (int i = 0; i < retrievedSources.size(); i++) {
            if (matchesAnyExpected(retrievedSources.get(i), question.expectedSources())) {
                relevantRetrieved++;
                if (firstRelevantRank == 0) {
                    firstRelevantRank = i + 1;
                }
            }
        }

        // Recall is over *expected sources found*, not chunks: several chunks from one expected
        // file should not inflate it.
        Set<String> expectedFound = new LinkedHashSet<>();
        for (String expected : question.expectedSources()) {
            if (retrievedSources.stream().anyMatch(p -> containsIgnoreCase(p, expected))) {
                expectedFound.add(expected);
            }
        }

        double precision = retrievedSources.isEmpty()
                ? 0.0 : (double) relevantRetrieved / retrievedSources.size();
        double recall = question.expectedSources().isEmpty()
                ? 1.0 : (double) expectedFound.size() / question.expectedSources().size();

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String keyword : question.expectedKeywords()) {
            if (containsIgnoreCase(response.answer(), keyword)) {
                matched.add(keyword);
            } else {
                missing.add(keyword);
            }
        }

        boolean citedCorrectly = citedSources.stream()
                .anyMatch(p -> matchesAnyExpected(p, question.expectedSources()));
        boolean answerCorrect = Answer.ANSWERED.equals(response.status())
                && !matched.isEmpty()
                && citedCorrectly;

        return new EvalReport.QuestionResult(
                question.id(), question.question(), response.status(), response.answer(),
                citedSources, retrievedSources, precision, recall, firstRelevantRank,
                matched, missing, answerCorrect, response.contextTruncated(), response.elapsedMs());
    }

    private static boolean matchesAnyExpected(String path, List<String> expected) {
        return expected.stream().anyMatch(e -> containsIgnoreCase(path, e));
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && needle != null
                && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private EvalReport.Config config(int topK, String mode) {
        SpecRagProperties.Chunking chunking = properties.getChunk();
        return new EvalReport.Config(
                chunking.getDefaultChunker(), chunking.getMaxTokens(), chunking.getOverlapTokens(),
                topK, mode, properties.getRetrieval().getMinScore(), properties.getContext().getMaxTokens(),
                embeddingClient.backendId(), store.backendId(), generator.id(), store.count());
    }

    private static EvalReport.Summary summarise(List<EvalReport.QuestionResult> results, long elapsed) {
        int n = results.size();
        if (n == 0) {
            return new EvalReport.Summary(0, 0, 0, 0, 0, 0, 0, 0, elapsed, 0);
        }
        double precision = results.stream().mapToDouble(EvalReport.QuestionResult::precisionAtK).sum() / n;
        double recall = results.stream().mapToDouble(EvalReport.QuestionResult::recallAtK).sum() / n;
        double mrr = results.stream()
                .mapToDouble(r -> r.firstRelevantRank() == 0 ? 0.0 : 1.0 / r.firstRelevantRank())
                .sum() / n;
        long correct = results.stream().filter(EvalReport.QuestionResult::answerCorrect).count();
        long answered = results.stream().filter(r -> Answer.ANSWERED.equals(r.status())).count();
        long truncated = results.stream().filter(EvalReport.QuestionResult::contextTruncated).count();
        long meanMs = (long) results.stream().mapToLong(EvalReport.QuestionResult::elapsedMs).average().orElse(0);

        return new EvalReport.Summary(n, precision, recall, mrr, (double) correct / n,
                (int) answered, (int) (n - answered), (int) truncated, elapsed, meanMs);
    }
}
