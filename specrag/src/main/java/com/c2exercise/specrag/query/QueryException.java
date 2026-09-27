package com.c2exercise.specrag.query;

import com.c2exercise.specrag.web.SpecRagException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/** Query failures with a defined HTTP response. */
public class QueryException extends SpecRagException {

    private QueryException(HttpStatus status, String message, Map<String, Object> body) {
        super(status, message, body);
    }

    /** AC-15 — a blank question is rejected, not embedded. */
    public static QueryException questionRequired() {
        return new QueryException(HttpStatus.BAD_REQUEST,
                "question must not be blank",
                Map.of("error", "question_required"));
    }

    /** AC-16 — an unknown source-type filter is a client error, not an empty result. */
    public static QueryException unknownSourceType(String requested, List<String> supported) {
        return new QueryException(HttpStatus.BAD_REQUEST,
                "unknown sourceType: " + requested,
                Map.of("error", "unknown_source_type",
                        "requested", requested,
                        "supported", supported));
    }

    /** TASK-009 — an unknown retrieval mode is refused rather than silently falling back. */
    public static QueryException unknownRetrievalMode(String requested, List<String> supported) {
        return new QueryException(HttpStatus.BAD_REQUEST,
                "unknown retrieval mode: " + requested,
                Map.of("error", "unknown_retrieval_mode",
                        "requested", requested,
                        "supported", supported));
    }

    /** The index has not been built yet; answering would be misleading. */
    public static QueryException indexEmpty() {
        return new QueryException(HttpStatus.CONFLICT,
                "index is empty — ingest a corpus first",
                Map.of("error", "index_empty"));
    }
}
