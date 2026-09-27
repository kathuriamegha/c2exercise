package com.c2exercise.specrag.web;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Base for failures that map to a defined HTTP response.
 *
 * <p>The status and the body live with the failure rather than with the controller, so the AC
 * that names an error code and the code that produces it sit in the same place.
 */
public abstract class SpecRagException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, Object> body;

    protected SpecRagException(HttpStatus status, String message, Map<String, Object> body) {
        super(message);
        this.status = status;
        this.body = body;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getBody() {
        return body;
    }
}
