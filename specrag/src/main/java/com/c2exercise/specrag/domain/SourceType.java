package com.c2exercise.specrag.domain;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Corpus file kinds we index. Drives section extraction and metadata filtering (AC-7, AC-16). */
public enum SourceType {
    MARKDOWN,
    JAVA;

    public static SourceType fromPath(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".md")) {
            return MARKDOWN;
        }
        if (name.endsWith(".java")) {
            return JAVA;
        }
        throw new IllegalArgumentException("Unsupported source file: " + path);
    }

    public static boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".md") || name.endsWith(".java");
    }

    /** Lowercase wire form used in the API, e.g. {@code "markdown"}. */
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Empty for an unrecognised filter, so the caller decides whether that is a 400 (AC-16). */
    public static Optional<SourceType> fromWire(String wire) {
        if (wire == null || wire.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(t -> t.wire().equalsIgnoreCase(wire.strip()))
                .findFirst();
    }

    public static List<String> wireNames() {
        return Arrays.stream(values()).map(SourceType::wire).toList();
    }
}
