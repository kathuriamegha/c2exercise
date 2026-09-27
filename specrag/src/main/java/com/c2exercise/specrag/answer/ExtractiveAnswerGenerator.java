package com.c2exercise.specrag.answer;

import com.c2exercise.specrag.domain.Chunk;
import com.c2exercise.specrag.domain.SourceType;
import com.c2exercise.specrag.retrieve.AssembledContext;
import com.c2exercise.specrag.retrieve.Citation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Answers by quoting the retrieved corpus rather than paraphrasing it.
 *
 * <p>D-2. There is no LLM in this build, and the substitute is not a worse LLM — it is a
 * generator that cannot hallucinate a citation, because every sentence it emits was copied out of
 * a chunk whose marker it carries. AC-17 is therefore true by construction rather than by test.
 *
 * <p>Retrieval and extraction are scored on different material. A chunk of Java is retrieved on
 * its whole text — identifiers carry real signal — but only its prose is worth quoting back:
 * quoting {@code Map.of("error",} answers nothing. So candidate sentences are drawn from doc
 * comments in Java and from body text in markdown, and code is left to the cited chunk, which the
 * caller can fetch by id.
 */
@Component
public class ExtractiveAnswerGenerator implements AnswerGenerator {

    private static final int MAX_SENTENCES = 3;
    private static final int MIN_SENTENCE_CHARS = 25;
    private static final int MIN_SENTENCE_WORDS = 5;

    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern TERM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern LINE_COMMENT = Pattern.compile("^\\s*//+\\s?");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("^\\s*(/\\*+|\\*+/?)\\s?");
    private static final Pattern JAVADOC_TAG = Pattern.compile("^\\s*@\\w+");
    private static final Pattern HTML_TAG = Pattern.compile("</?[a-z]+>");

    /** Small and deliberately boring: these words carry no retrieval signal in this corpus. */
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "had", "her",
            "was", "one", "our", "out", "has", "have", "how", "its", "who", "did", "does", "what",
            "when", "where", "which", "with", "this", "that", "from", "into", "than", "then",
            "they", "them", "there", "their", "been", "being", "were", "will", "would", "should",
            "could", "about", "why");

    @Override
    public String id() {
        return "extractive";
    }

    @Override
    public Answer generate(String question, AssembledContext context) {
        if (context.isEmpty()) {
            return Answer.noRelevantContext(id());
        }

        Set<String> questionTerms = terms(question);
        List<Candidate> candidates = new ArrayList<>();

        for (Citation citation : context.citations()) {
            Chunk chunk = context.included().get(citation.marker() - 1).chunk();
            for (String sentence : prose(chunk)) {
                int overlap = overlap(questionTerms, terms(sentence));
                if (overlap > 0) {
                    candidates.add(new Candidate(sentence, citation, overlap));
                }
            }
        }

        if (candidates.isEmpty()) {
            return fallback(context);
        }

        // Weighting overlap by the chunk's cosine score was tried here and measured: it moved one
        // citation out of fifteen and changed answer accuracy in none of six configurations
        // (SPEC-002 §9.3). Cosine over the top-K sits in a narrow band, so it cannot outvote an
        // integer overlap count — the same units argument this project makes against score-based
        // fusion in Retriever. Reverted rather than kept as unmeasured complexity.
        candidates.sort(Comparator
                .comparingInt(Candidate::overlap).reversed()
                .thenComparing(c -> -c.citation().score())
                .thenComparingInt(c -> c.citation().marker()));

        StringBuilder text = new StringBuilder();
        Set<Citation> used = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Candidate candidate : candidates) {
            if (used.size() >= MAX_SENTENCES) {
                break;
            }
            if (!seen.add(candidate.sentence())) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(candidate.sentence()).append(" [").append(candidate.citation().marker()).append(']');
            used.add(candidate.citation());
        }

        return new Answer(Answer.ANSWERED, text.toString(), List.copyOf(used), id());
    }

    /**
     * No prose overlapped the question, but retrieval already cleared the relevance floor. Name
     * the best source instead of claiming ignorance — the caller can read the chunk itself.
     */
    private Answer fallback(AssembledContext context) {
        Citation top = context.citations().get(0);
        Chunk chunk = context.included().get(0).chunk();
        List<String> prose = prose(chunk);
        String text = prose.isEmpty()
                ? "The most relevant source is " + chunk.citationLabel() + ". [" + top.marker() + "]"
                : prose.get(0) + " [" + top.marker() + "]";
        return new Answer(Answer.ANSWERED, text, List.of(top), id());
    }

    /** Prose sentences worth quoting, in document order. */
    private static List<String> prose(Chunk chunk) {
        String text = chunk.sourceType() == SourceType.JAVA
                ? commentText(chunk.content())
                : markdownText(chunk.content());

        List<String> out = new ArrayList<>();
        for (String sentence : SENTENCE_BOUNDARY.split(text)) {
            String trimmed = sentence.strip();
            if (trimmed.length() >= MIN_SENTENCE_CHARS && wordCount(trimmed) >= MIN_SENTENCE_WORDS) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /** Javadoc and {@code //} comments, with their markers and tags stripped. */
    private static String commentText(String java) {
        StringBuilder out = new StringBuilder();
        for (String line : java.split("\n")) {
            String stripped = line.strip();
            boolean isComment = stripped.startsWith("//")
                    || stripped.startsWith("*")
                    || stripped.startsWith("/*");
            if (!isComment || JAVADOC_TAG.matcher(stripped).find()) {
                continue;
            }
            String cleaned = BLOCK_COMMENT.matcher(LINE_COMMENT.matcher(stripped).replaceFirst(""))
                    .replaceFirst("");
            cleaned = HTML_TAG.matcher(cleaned).replaceAll("").strip();
            if (!cleaned.isEmpty()) {
                out.append(cleaned).append(' ');
            }
        }
        return out.toString();
    }

    /** Markdown body text: fenced code, tables and heading markers are not answer material. */
    private static String markdownText(String markdown) {
        StringBuilder out = new StringBuilder();
        boolean inFence = false;
        for (String line : markdown.split("\n")) {
            String stripped = line.strip();
            if (stripped.startsWith("```")) {
                inFence = !inFence;
                continue;
            }
            if (inFence || stripped.isEmpty() || stripped.startsWith("|") || stripped.startsWith("#")) {
                continue;
            }
            out.append(stripped).append(' ');
        }
        return out.toString();
    }

    private static int wordCount(String sentence) {
        return sentence.split("\\s+").length;
    }

    private static Set<String> terms(String text) {
        Set<String> out = new LinkedHashSet<>();
        for (String raw : TERM.split(text.toLowerCase())) {
            if (raw.length() >= 3 && !STOPWORDS.contains(raw)) {
                out.add(raw);
            }
        }
        return out;
    }

    private static int overlap(Set<String> questionTerms, Set<String> sentenceTerms) {
        int hits = 0;
        for (String term : questionTerms) {
            if (sentenceTerms.contains(term)) {
                hits++;
            }
        }
        return hits;
    }

    private record Candidate(String sentence, Citation citation, int overlap) {
    }
}
