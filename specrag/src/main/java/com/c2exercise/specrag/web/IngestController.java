package com.c2exercise.specrag.web;

import com.c2exercise.specrag.ingest.IngestResult;
import com.c2exercise.specrag.ingest.IngestService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** TASK-012. AC-1..AC-5. */
@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping
    public IngestResult ingest(@RequestBody IngestRequest request) {
        return ingestService.ingest(request.path(), request.chunker());
    }

    /**
     * @param path    directory to index; must resolve inside {@code specrag.corpus.allowed-root}
     * @param chunker optional strategy id; defaults to {@code specrag.chunk.default-chunker}
     */
    public record IngestRequest(String path, String chunker) {
    }
}
