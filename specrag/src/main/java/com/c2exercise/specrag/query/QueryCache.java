package com.c2exercise.specrag.query;

import com.c2exercise.specrag.ingest.IndexRebuiltEvent;
import com.c2exercise.specrag.retrieve.RetrievalMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TASK-013 / NFR-5. An LRU of answered queries, keyed by everything that affects the answer.
 *
 * <p>The saving is the question embedding plus the full store scan, which is the expensive part
 * of a query on this corpus. Correctness rests on one rule: the cache is dropped whenever the
 * index is rebuilt, so a cached answer can never cite a chunk that no longer exists.
 */
@Component
public class QueryCache {

    private static final Logger log = LoggerFactory.getLogger(QueryCache.class);
    private static final int MAX_ENTRIES = 128;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    private final Map<String, QueryResponse> entries =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, QueryResponse> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    /**
     * Keyed by everything that changes the answer — the retrieval mode included, so an A/B run
     * over one warm process measures two retrievers rather than one retriever and its cache.
     */
    public static String key(QueryRequest request, int effectiveTopK, RetrievalMode mode) {
        return request.question().strip().toLowerCase()
                + "|k=" + effectiveTopK
                + "|type=" + (request.sourceType() == null ? "" : request.sourceType().strip().toLowerCase())
                + "|mode=" + mode.wire();
    }

    public Optional<QueryResponse> get(String key) {
        synchronized (entries) {
            QueryResponse hit = entries.get(key);
            if (hit == null) {
                misses.incrementAndGet();
                return Optional.empty();
            }
            hits.incrementAndGet();
            return Optional.of(hit);
        }
    }

    public void put(String key, QueryResponse response) {
        synchronized (entries) {
            entries.put(key, response);
        }
    }

    /** Invalidation is all-or-nothing: a rebuild can change any chunk id in any cached answer. */
    @EventListener
    public void onIndexRebuilt(IndexRebuiltEvent event) {
        int dropped;
        synchronized (entries) {
            dropped = entries.size();
            entries.clear();
        }
        log.info("Index rebuilt ({} chunks) — dropped {} cached queries", event.chunkCount(), dropped);
    }

    public CacheStats stats() {
        synchronized (entries) {
            return new CacheStats(entries.size(), MAX_ENTRIES, hits.get(), misses.get());
        }
    }

    public record CacheStats(int size, int maxEntries, long hits, long misses) {
        public double hitRate() {
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        }
    }
}
