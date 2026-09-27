package com.c2exercise.specrag.store;

import com.c2exercise.specrag.domain.Chunk;
import com.c2exercise.specrag.domain.SourceType;
import com.c2exercise.specrag.embed.Vectors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Exact nearest-neighbour search by full scan.
 *
 * <p>At this corpus size a full scan is not a compromise — it is the correct choice. Scoring
 * ~150 vectors of 384 floats is well under a millisecond, and unlike an ANN index it returns
 * the true top-K with no recall loss to tune away. The default store (SPEC-002 D-1).
 */
public class InMemoryVectorStore implements VectorStore {

    private final Map<UUID, StoredChunk> chunks = new LinkedHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public String backendId() {
        return "in-memory";
    }

    @Override
    public void replaceAll(List<StoredChunk> replacement) {
        lock.writeLock().lock();
        try {
            chunks.clear();
            for (StoredChunk sc : replacement) {
                chunks.put(sc.chunk().id(), sc);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int count() {
        lock.readLock().lock();
        try {
            return chunks.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<Chunk> byId(UUID id) {
        lock.readLock().lock();
        try {
            StoredChunk sc = chunks.get(id);
            return sc == null ? Optional.empty() : Optional.of(sc.chunk());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Chunk> list(int limit, SourceType sourceTypeFilter) {
        lock.readLock().lock();
        try {
            List<Chunk> out = new ArrayList<>();
            for (StoredChunk sc : chunks.values()) {
                if (out.size() >= limit) {
                    break;
                }
                if (sourceTypeFilter == null || sc.chunk().sourceType() == sourceTypeFilter) {
                    out.add(sc.chunk());
                }
            }
            return out;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<ScoredChunk> search(float[] queryVector, int topK, SourceType sourceTypeFilter) {
        if (topK <= 0) {
            return List.of();
        }
        lock.readLock().lock();
        try {
            List<ScoredChunk> scored = new ArrayList<>(chunks.size());
            for (StoredChunk sc : chunks.values()) {
                if (sourceTypeFilter != null && sc.chunk().sourceType() != sourceTypeFilter) {
                    continue;
                }
                scored.add(new ScoredChunk(sc.chunk(), Vectors.cosine(queryVector, sc.embedding())));
            }
            Collections.sort(scored);
            // AC-14: asking for more than we hold returns what we hold, not an error.
            return scored.subList(0, Math.min(topK, scored.size()));
        } finally {
            lock.readLock().unlock();
        }
    }
}
