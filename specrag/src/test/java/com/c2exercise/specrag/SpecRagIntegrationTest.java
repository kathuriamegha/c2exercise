package com.c2exercise.specrag;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * TASK-015. Exercises the acceptance criteria against the running application over HTTP.
 *
 * <p>The corpus is a handful of files written by the test rather than the project's own source,
 * so the expected answers are fixed by the test and cannot drift when the code is edited.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SpecRagIntegrationTest {

    private static final Path CORPUS = createCorpus();

    @Autowired
    private MockMvc mockMvc;

    private MockMvcTester http;

    @DynamicPropertySource
    static void corpusRoot(DynamicPropertyRegistry registry) {
        registry.add("specrag.corpus.allowed-root", CORPUS::toString);
    }

    @BeforeAll
    static void announce() {
        // Nothing to do; kept so the corpus path is obvious in a failure trace.
    }

    private MockMvcTester http() {
        if (http == null) {
            http = MockMvcTester.create(mockMvc);
        }
        return http;
    }

    private static Path createCorpus() {
        try {
            Path root = Files.createTempDirectory("specrag-corpus");
            Files.writeString(root.resolve("retrieval.md"), """
                    # Retrieval

                    ## Relevance floor

                    Chunks scoring below the configured floor are discarded before assembly, so the
                    generator is never handed weak context that it might present as an answer.

                    ## Citations

                    Every citation names a chunk that is present in the assembled context, which
                    means a citation can always be fetched by its identifier and read in full.
                    """);
            Files.writeString(root.resolve("Widget.java"), """
                    package demo;

                    /**
                     * A widget that counts things for the purposes of this test corpus.
                     *
                     * The counter is deliberately not thread safe, because the surrounding code
                     * holds a lock for the whole operation and a second lock would only confuse
                     * the reader without making anything safer.
                     */
                    public class Widget {

                        private int count;

                        /** Increments the counter and returns the new value to the caller. */
                        public int increment() {
                            return ++count;
                        }
                    }
                    """);
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @Order(1)
    @DisplayName("AC-1: ingest indexes the corpus and reports what produced the index")
    void ingestIndexesCorpus() {
        assertThat(http().post().uri("/api/ingest").contentType(APPLICATION_JSON)
                .content("{\"path\":\"" + CORPUS + "\"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.filesIngested").isEqualTo(2);

        assertThat(http().get().uri("/api/chunks/count"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.count").asNumber().isNotEqualTo(0);
    }

    @Test
    @Order(2)
    @DisplayName("AC-5: re-ingesting the same corpus does not duplicate chunks")
    void reIngestIsIdempotent() {
        int first = chunkCount();
        http().post().uri("/api/ingest").contentType(APPLICATION_JSON)
                .content("{\"path\":\"" + CORPUS + "\"}").exchange();
        assertThat(chunkCount()).isEqualTo(first);
    }

    @Test
    @Order(3)
    @DisplayName("AC-3: a path outside the allowed root is refused with 403")
    void pathOutsideAllowedRootIsRefused() {
        assertThat(http().post().uri("/api/ingest").contentType(APPLICATION_JSON)
                .content("{\"path\":\"/etc\"}"))
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.error").isEqualTo("path_outside_allowed_root");
    }

    @Test
    @Order(4)
    @DisplayName("AC-3: traversal through the allowed root is refused, not normalised away")
    void traversalOutOfAllowedRootIsRefused() {
        assertThat(http().post().uri("/api/ingest").contentType(APPLICATION_JSON)
                .content("{\"path\":\"" + CORPUS + "/../../..\"}"))
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.error").isEqualTo("path_outside_allowed_root");
    }

    @Test
    @Order(5)
    @DisplayName("AC-2: a path that does not exist is refused with 400")
    void missingPathIsRefused() {
        assertThat(http().post().uri("/api/ingest").contentType(APPLICATION_JSON)
                .content("{\"path\":\"" + CORPUS + "/nope\"}"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.error").isEqualTo("path_not_found");
    }

    @Test
    @Order(6)
    @DisplayName("AC-4: an unknown chunker is refused and the supported ids are named")
    void unknownChunkerIsRefused() {
        assertThat(http().post().uri("/api/ingest").contentType(APPLICATION_JSON)
                .content("{\"path\":\"" + CORPUS + "\",\"chunker\":\"semantic-magic\"}"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.supported").asArray().contains("recursive");
    }

    @Test
    @Order(7)
    @DisplayName("AC-12/AC-17: an answered query cites chunks that can be fetched by id")
    void answerCitesRetrievableChunks() {
        ingest();
        var response = http().post().uri("/api/query").contentType(APPLICATION_JSON)
                .content("{\"question\":\"What happens to chunks scoring below the relevance floor?\"}")
                .exchange();

        assertThat(response).hasStatusOk();
        var json = assertThat(response).bodyJson();
        json.extractingPath("$.status").isEqualTo("answered");
        json.extractingPath("$.citations").asArray().isNotEmpty();

        String chunkId = json.extractingPath("$.citations[0].chunkId").asString().actual();
        assertThat(http().get().uri("/api/chunks/" + chunkId))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.content").asString().isNotEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("AC-15: a blank question is rejected rather than embedded")
    void blankQuestionIsRejected() {
        assertThat(http().post().uri("/api/query").contentType(APPLICATION_JSON)
                .content("{\"question\":\"   \"}"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.error").isEqualTo("question_required");
    }

    @Test
    @Order(9)
    @DisplayName("AC-16: a source-type filter restricts retrieval to that type")
    void sourceTypeFilterRestrictsRetrieval() {
        ingest();
        var response = http().post().uri("/api/query").contentType(APPLICATION_JSON)
                .content("{\"question\":\"Why is the counter not thread safe?\",\"sourceType\":\"java\"}")
                .exchange();

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson()
                .extractingPath("$.retrieved..sourceType").asArray()
                .allMatch("java"::equals);
    }

    @Test
    @Order(10)
    @DisplayName("AC-16: an unrecognised source-type filter is a client error, not an empty result")
    void unknownSourceTypeIsRefused() {
        assertThat(http().post().uri("/api/query").contentType(APPLICATION_JSON)
                .content("{\"question\":\"anything\",\"sourceType\":\"cobol\"}"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.error").isEqualTo("unknown_source_type");
    }

    @Test
    @Order(11)
    @DisplayName("AC-13: an off-corpus question returns no_relevant_context, not a guess")
    void offCorpusQuestionIsNotAnswered() {
        ingest();
        assertThat(http().post().uri("/api/query").contentType(APPLICATION_JSON)
                .content("{\"question\":\"What is the tensile strength of reinforced concrete in seawater?\"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status").isEqualTo("no_relevant_context");
    }

    @Test
    @Order(12)
    @DisplayName("AC-14: topK caps results and never exceeds what the index holds")
    void topKIsRespected() {
        ingest();
        assertThat(http().post().uri("/api/query").contentType(APPLICATION_JSON)
                .content("{\"question\":\"What does a citation name?\",\"topK\":2}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.retrieved").asArray().hasSizeLessThanOrEqualTo(2);
    }

    @Test
    @Order(13)
    @DisplayName("NFR-5: an identical repeated query is served from cache")
    void repeatedQueryIsCached() {
        ingest();
        String body = "{\"question\":\"What does a citation name in the assembled context?\"}";
        http().post().uri("/api/query").contentType(APPLICATION_JSON).content(body).exchange();

        assertThat(http().post().uri("/api/query").contentType(APPLICATION_JSON).content(body))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.cached").isEqualTo(true);
    }

    @Test
    @Order(14)
    @DisplayName("NFR-5: rebuilding the index drops the cache, so no answer cites a dead chunk")
    void ingestInvalidatesCache() {
        ingest();
        String body = "{\"question\":\"What does a citation name in the assembled context?\"}";
        http().post().uri("/api/query").contentType(APPLICATION_JSON).content(body).exchange();
        ingest();

        assertThat(http().post().uri("/api/query").contentType(APPLICATION_JSON).content(body))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.cached").isEqualTo(false);
    }

    @Test
    @Order(15)
    @DisplayName("TASK-009: hybrid mode answers and says so, and the floor still governs it")
    void hybridModeAnswers() {
        ingest();
        var response = http().post().uri("/api/query").contentType(APPLICATION_JSON)
                .content("{\"question\":\"What happens to chunks scoring below the relevance floor?\","
                        + "\"mode\":\"hybrid\"}")
                .exchange();

        assertThat(response).hasStatusOk();
        var json = assertThat(response).bodyJson();
        json.extractingPath("$.retrievalMode").isEqualTo("hybrid");
        json.extractingPath("$.status").isEqualTo("answered");
        // Fusion reorders the relevant set; it must not widen it past the floor (AC-13).
        json.extractingPath("$.retrieved..score").asArray().allSatisfy(score ->
                assertThat(((Number) score).doubleValue()).isGreaterThanOrEqualTo(0.25));
    }

    @Test
    @Order(16)
    @DisplayName("TASK-009: hybrid does not answer an off-corpus question either")
    void hybridStillRefusesOffCorpusQuestions() {
        ingest();
        assertThat(http().post().uri("/api/query").contentType(APPLICATION_JSON)
                .content("{\"question\":\"What is the tensile strength of reinforced concrete in seawater?\","
                        + "\"mode\":\"hybrid\"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status").isEqualTo("no_relevant_context");
    }

    @Test
    @Order(17)
    @DisplayName("TASK-009: an unknown mode is refused, so a typo cannot measure the default")
    void unknownRetrievalModeIsRefused() {
        assertThat(http().post().uri("/api/query").contentType(APPLICATION_JSON)
                .content("{\"question\":\"anything\",\"mode\":\"magic\"}"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.error").isEqualTo("unknown_retrieval_mode");
    }

    @Test
    @Order(18)
    @DisplayName("TASK-009: the cache keys on mode, so an A/B run measures retrievers not cache")
    void modeIsPartOfTheCacheKey() {
        ingest();
        String question = "\"question\":\"Which chunks does the assembled context include?\"";
        http().post().uri("/api/query").contentType(APPLICATION_JSON)
                .content("{" + question + ",\"mode\":\"semantic\"}").exchange();

        assertThat(http().post().uri("/api/query").contentType(APPLICATION_JSON)
                .content("{" + question + ",\"mode\":\"hybrid\"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.cached").isEqualTo(false);
    }

    private void ingest() {
        http().post().uri("/api/ingest").contentType(APPLICATION_JSON)
                .content("{\"path\":\"" + CORPUS + "\"}").exchange();
    }

    private int chunkCount() {
        return assertThat(http().get().uri("/api/chunks/count"))
                .bodyJson().extractingPath("$.count").asNumber().actual().intValue();
    }
}
