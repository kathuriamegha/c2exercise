package com.c2exercise.specrag.retrieve;

import com.c2exercise.specrag.chunk.TextTokenizer;
import com.c2exercise.specrag.config.SpecRagProperties;
import com.c2exercise.specrag.domain.Chunk;
import com.c2exercise.specrag.store.ScoredChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * TASK-010. Packs retrieved chunks into a token-bounded context.
 *
 * <p>This is the component that makes context-window overflow a handled case rather than a
 * failure mode: the budget is enforced in real model tokens, and anything that did not fit is
 * reported as {@code truncated} instead of silently disappearing (AC-18).
 */
@Component
public class ContextAssembler {

    private final TextTokenizer tokenizer;
    private final SpecRagProperties properties;

    public ContextAssembler(TextTokenizer tokenizer, SpecRagProperties properties) {
        this.tokenizer = tokenizer;
        this.properties = properties;
    }

    public AssembledContext assemble(List<ScoredChunk> hits) {
        int budget = properties.getContext().getMaxTokens();

        List<ScoredChunk> included = new ArrayList<>();
        List<Citation> citations = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int used = 0;
        boolean truncated = false;

        for (ScoredChunk hit : hits) {
            int marker = included.size() + 1;
            String passage = render(marker, hit);
            int cost = tokenizer.count(passage);

            if (used + cost > budget) {
                if (included.isEmpty()) {
                    // A single passage larger than the whole budget: keep the best one, clipped,
                    // rather than answering "no relevant context" when relevant context exists.
                    passage = clipToTokens(passage, budget);
                    cost = tokenizer.count(passage);
                    append(text, passage);
                    used += cost;
                    included.add(hit);
                    citations.add(citationFor(marker, hit));
                }
                truncated = true;
                break;
            }

            append(text, passage);
            used += cost;
            included.add(hit);
            citations.add(citationFor(marker, hit));
        }

        return new AssembledContext(text.toString(), List.copyOf(included), List.copyOf(citations),
                used, truncated);
    }

    private static void append(StringBuilder text, String passage) {
        if (!text.isEmpty()) {
            text.append("\n\n");
        }
        text.append(passage);
    }

    private static String render(int marker, ScoredChunk hit) {
        Chunk chunk = hit.chunk();
        return "[" + marker + "] " + chunk.citationLabel() + "\n" + chunk.content();
    }

    private static Citation citationFor(int marker, ScoredChunk hit) {
        Chunk chunk = hit.chunk();
        return new Citation(marker, chunk.id(), chunk.sourcePath(), chunk.section(), hit.score());
    }

    private String clipToTokens(String passage, int budget) {
        long[] ids = tokenizer.encode(passage);
        if (ids.length <= budget) {
            return passage;
        }
        long[] head = new long[budget];
        System.arraycopy(ids, 0, head, 0, budget);
        return tokenizer.decode(head);
    }
}
