package com.c2exercise.specrag.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Turns {@link SpecRagException} into the exact bodies AC-2, AC-3, AC-4, AC-15 and AC-16 name. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SpecRagException.class)
    public ResponseEntity<Map<String, Object>> handleSpecRag(SpecRagException e) {
        log.debug("Rejected request: {}", e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(e.getBody());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "invalid_request", "message", String.valueOf(e.getMessage())));
    }
}
