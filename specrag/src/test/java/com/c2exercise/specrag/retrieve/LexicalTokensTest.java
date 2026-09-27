package com.c2exercise.specrag.retrieve;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-009. The lexical half of hybrid retrieval is only worth having if it matches the way this
 * corpus actually spells things, which is as identifiers rather than as English.
 */
class LexicalTokensTest {

    @Test
    void identifiersMatchTheWordsAQuestionWouldUse() {
        List<String> tokens = LexicalTokens.of("throw IngestException.pathOutsideAllowedRoot(path)");

        assertTrue(tokens.containsAll(List.of("path", "outside", "allowed", "root")),
                "camelCase must split so a question phrased in words can match: " + tokens);
        assertTrue(tokens.contains("pathoutsideallowedroot"),
                "the whole identifier is kept too, so an exact query still scores highest: " + tokens);
    }

    @Test
    void snakeCaseSplitsTheSameWay() {
        List<String> tokens = LexicalTokens.of("\"error\": \"no_relevant_context\"");

        assertTrue(tokens.containsAll(List.of("no_relevant_context", "relevant", "context")),
                "underscored wire values must match a question about relevant context: " + tokens);
    }

    @Test
    void stopwordsAndSingleCharactersAreDropped() {
        List<String> tokens = LexicalTokens.of("What is the score of a chunk?");

        assertFalse(tokens.contains("the"), "stopwords carry no signal: " + tokens);
        assertFalse(tokens.contains("a"), "single characters carry no signal: " + tokens);
        assertTrue(tokens.containsAll(List.of("score", "chunk")), tokens.toString());
    }
}
