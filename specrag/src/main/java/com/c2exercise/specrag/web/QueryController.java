package com.c2exercise.specrag.web;

import com.c2exercise.specrag.query.QueryCache;
import com.c2exercise.specrag.query.QueryRequest;
import com.c2exercise.specrag.query.QueryResponse;
import com.c2exercise.specrag.query.QueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** TASK-012. AC-12..AC-18. */
@RestController
@RequestMapping("/api")
public class QueryController {

    private final QueryService queryService;
    private final QueryCache cache;

    public QueryController(QueryService queryService, QueryCache cache) {
        this.queryService = queryService;
        this.cache = cache;
    }

    @PostMapping("/query")
    public QueryResponse query(@RequestBody(required = false) QueryRequest request) {
        return queryService.query(request);
    }

    /** NFR-5 verification hook: hit rate is observable without reading logs. */
    @GetMapping("/cache")
    public Map<String, Object> cacheStats() {
        QueryCache.CacheStats stats = cache.stats();
        return Map.of(
                "size", stats.size(),
                "maxEntries", stats.maxEntries(),
                "hits", stats.hits(),
                "misses", stats.misses(),
                "hitRate", stats.hitRate());
    }
}
