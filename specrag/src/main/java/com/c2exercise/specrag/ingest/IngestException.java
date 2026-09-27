package com.c2exercise.specrag.ingest;

import com.c2exercise.specrag.web.SpecRagException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * Ingest failures that map to a defined HTTP response.
 *
 * <p>Each carries the error code the AC names, so the controller does not have to re-derive it —
 * the AC-2 / AC-3 / AC-4 bodies are produced from here.
 */
public class IngestException extends SpecRagException {

    private IngestException(HttpStatus status, String message, Map<String, Object> body) {
        super(status, message, body);
    }

    /** AC-2 */
    public static IngestException pathNotFound(String path) {
        return new IngestException(HttpStatus.BAD_REQUEST,
                "path not found: " + path,
                Map.of("error", "path_not_found", "path", path));
    }

    /** AC-3 — the traversal guard. */
    public static IngestException pathOutsideAllowedRoot(String path, String allowedRoot) {
        return new IngestException(HttpStatus.FORBIDDEN,
                "path escapes allowed root: " + path,
                Map.of("error", "path_outside_allowed_root",
                        "path", path,
                        "allowedRoot", allowedRoot));
    }

    /** AC-4 */
    public static IngestException unknownChunker(String requested, List<String> supported) {
        return new IngestException(HttpStatus.BAD_REQUEST,
                "unknown chunker: " + requested,
                Map.of("error", "unknown_chunker",
                        "requested", requested,
                        "supported", supported));
    }
}
