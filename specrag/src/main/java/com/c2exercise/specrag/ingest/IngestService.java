package com.c2exercise.specrag.ingest;

import com.c2exercise.specrag.chunk.Chunker;
import com.c2exercise.specrag.config.SpecRagProperties;
import com.c2exercise.specrag.domain.Chunk;
import com.c2exercise.specrag.domain.SourceType;
import com.c2exercise.specrag.embed.EmbeddingClient;
import com.c2exercise.specrag.store.StoredChunk;
import com.c2exercise.specrag.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** TASK-007. Walk → chunk → embed → store, as a full index rebuild (AC-1..AC-5). */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final Map<String, Chunker> chunkers = new LinkedHashMap<>();
    private final EmbeddingClient embeddingClient;
    private final VectorStore store;
    private final SpecRagProperties properties;
    private final ApplicationEventPublisher events;

    public IngestService(List<Chunker> availableChunkers,
                         EmbeddingClient embeddingClient,
                         VectorStore store,
                         SpecRagProperties properties,
                         ApplicationEventPublisher events) {
        for (Chunker c : availableChunkers) {
            this.chunkers.put(c.id(), c);
        }
        this.embeddingClient = embeddingClient;
        this.store = store;
        this.properties = properties;
        this.events = events;
    }

    public IngestResult ingest(String requestedPath, String requestedChunker) {
        long start = System.currentTimeMillis();

        Chunker chunker = resolveChunker(requestedChunker);
        Path root = resolveWithinAllowedRoot(requestedPath);

        List<Path> files = findCorpusFiles(root);
        List<StoredChunk> stored = new ArrayList<>();
        int filesIngested = 0;

        for (Path file : files) {
            String content;
            try {
                content = Files.readString(file);
            } catch (IOException e) {
                // A single unreadable file should not abort the whole rebuild.
                log.warn("Skipping unreadable file {}: {}", file, e.getMessage());
                continue;
            }
            if (content.isBlank()) {
                continue;
            }
            String relative = relativize(root, file);
            SourceType type = SourceType.fromPath(file);

            List<Chunk> chunks = chunker.chunk(content, relative, type);
            for (Chunk chunk : chunks) {
                stored.add(new StoredChunk(chunk, embeddingClient.embed(chunk.content())));
            }
            filesIngested++;
        }

        // AC-5: full replace, so an identical re-ingest is idempotent rather than additive.
        store.replaceAll(stored);
        events.publishEvent(new IndexRebuiltEvent(stored.size(), chunker.id()));

        long elapsed = System.currentTimeMillis() - start;
        log.info("Ingested {} files into {} chunks using '{}' in {} ms",
                filesIngested, stored.size(), chunker.id(), elapsed);

        return new IngestResult(filesIngested, stored.size(), chunker.id(),
                embeddingClient.backendId(), store.backendId(), elapsed);
    }

    private Chunker resolveChunker(String requested) {
        String id = (requested == null || requested.isBlank())
                ? properties.getChunk().getDefaultChunker()
                : requested;
        Chunker chunker = chunkers.get(id);
        if (chunker == null) {
            throw IngestException.unknownChunker(id, List.copyOf(chunkers.keySet()));
        }
        return chunker;
    }

    /**
     * AC-3. Resolves symlinks and {@code ..} before comparing, because a check against the
     * literal string would be defeated by either.
     */
    private Path resolveWithinAllowedRoot(String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            throw IngestException.pathNotFound(String.valueOf(requestedPath));
        }
        Path allowedRoot = Path.of(properties.getCorpus().getAllowedRoot()).toAbsolutePath().normalize();
        Path candidate = Path.of(requestedPath).toAbsolutePath().normalize();

        Path realRoot = toRealPath(allowedRoot);
        Path realCandidate = toRealPathIfExists(candidate);

        if (realCandidate == null || !Files.isDirectory(realCandidate)) {
            throw IngestException.pathNotFound(requestedPath);
        }
        if (!realCandidate.startsWith(realRoot)) {
            throw IngestException.pathOutsideAllowedRoot(requestedPath, realRoot.toString());
        }
        return realCandidate;
    }

    private static Path toRealPath(Path p) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            return p;
        }
    }

    private static Path toRealPathIfExists(Path p) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            return null;
        }
    }

    private List<Path> findCorpusFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(SourceType::isSupported)
                    .filter(p -> !p.toString().contains("/target/"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk corpus at " + root, e);
        }
    }

    private static String relativize(Path root, Path file) {
        return root.relativize(file).toString();
    }
}
