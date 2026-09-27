package com.c2exercise.specrag.web;

import com.c2exercise.specrag.domain.Chunk;
import com.c2exercise.specrag.domain.SourceType;
import com.c2exercise.specrag.query.QueryException;
import com.c2exercise.specrag.store.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inspection endpoints. These exist so AC-17 is checkable from outside the process: every
 * citation a query returns can be fetched by id and compared against the answer text.
 */
@RestController
@RequestMapping("/api/chunks")
public class ChunkController {

    private final VectorStore store;

    public ChunkController(VectorStore store) {
        this.store = store;
    }

    @GetMapping
    public List<ChunkView> list(@RequestParam(defaultValue = "20") int limit,
                                @RequestParam(required = false) String sourceType) {
        SourceType filter = sourceType == null || sourceType.isBlank()
                ? null
                : SourceType.fromWire(sourceType).orElseThrow(
                        () -> QueryException.unknownSourceType(sourceType, SourceType.wireNames()));
        return store.list(limit, filter).stream().map(ChunkView::of).toList();
    }

    @GetMapping("/count")
    public Map<String, Object> count() {
        return Map.of("count", store.count(), "store", store.backendId());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> byId(@PathVariable UUID id) {
        return store.byId(id)
                .<ResponseEntity<?>>map(c -> ResponseEntity.ok(ChunkView.of(c)))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("error", "chunk_not_found", "id", id.toString())));
    }

    /** The chunk as the API exposes it — content included, because verifying a citation means reading it. */
    public record ChunkView(String id, String sourcePath, String section, String sourceType,
                            int ordinal, int tokenCount, String content) {

        static ChunkView of(Chunk c) {
            return new ChunkView(c.id().toString(), c.sourcePath(), c.section(),
                    c.sourceType().wire(), c.ordinal(), c.tokenCount(), c.content());
        }
    }
}
