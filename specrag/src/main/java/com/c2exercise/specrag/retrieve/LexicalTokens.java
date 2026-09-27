package com.c2exercise.specrag.retrieve;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tokenisation for lexical matching.
 *
 * <p>Deliberately different from the model's tokenizer, which exists to count budget. What
 * matters here is that a code corpus is not English: {@code allowedRoot} must match a question
 * asking about "the allowed root", and {@code path_outside_allowed_root} must match "path outside
 * allowed root". So identifiers are split on case and on underscores, and both the parts and the
 * whole are indexed — the whole so an exact identifier query still scores highest.
 */
public final class LexicalTokens {

    private static final Pattern NON_WORD = Pattern.compile("[^A-Za-z0-9_]+");
    private static final Pattern CASE_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|_+");
    private static final int MIN_LENGTH = 2;

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "has", "have",
            "how", "its", "who", "did", "does", "what", "when", "where", "which", "with", "this",
            "that", "from", "into", "than", "then", "they", "them", "there", "their", "been",
            "being", "were", "will", "would", "should", "could", "about", "why");

    private LexicalTokens() {
    }

    public static List<String> of(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String raw : NON_WORD.split(text)) {
            if (raw.isEmpty()) {
                continue;
            }
            add(out, raw.toLowerCase(Locale.ROOT));
            String[] parts = CASE_BOUNDARY.split(raw);
            if (parts.length > 1) {
                for (String part : parts) {
                    add(out, part.toLowerCase(Locale.ROOT));
                }
            }
        }
        return out;
    }

    private static void add(List<String> out, String token) {
        if (token.length() >= MIN_LENGTH && !STOPWORDS.contains(token)) {
            out.add(token);
        }
    }
}
