package com.c2exercise.specrag.chunk;

import com.c2exercise.specrag.domain.SourceType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the {@code section} metadata required by AC-7 — the nearest enclosing markdown heading,
 * or the nearest Java type/member declaration.
 *
 * <p>This is what makes a citation readable: "SPEC-001 § Drift Log" rather than a bare file path.
 */
public class SectionTracker {

    private static final Pattern MD_HEADING = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*#*\\s*$");

    private static final Pattern JAVA_TYPE = Pattern.compile(
            "^\\s*(?:public|protected|private|abstract|final|static|sealed|non-sealed|\\s)*"
                    + "(class|interface|record|enum)\\s+(\\w+)");

    private static final Pattern JAVA_MEMBER = Pattern.compile(
            "^\\s+(?:public|protected|private|abstract|final|static|synchronized|default|native|\\s)*"
                    + "(?:<[^>]+>\\s*)?[\\w.$\\[\\]<>,?\\s]+\\s+(\\w+)\\s*\\([^;]*\\)\\s*(?:throws [\\w.,\\s]+)?\\s*\\{");

    private final SourceType sourceType;
    private String currentType = "";
    private String current = "";

    public SectionTracker(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    /** Feed one line of the source in order; returns the section in effect after that line. */
    public String accept(String line) {
        if (sourceType == SourceType.MARKDOWN) {
            Matcher m = MD_HEADING.matcher(line);
            if (m.matches()) {
                current = m.group(2).trim();
            }
            return current;
        }

        Matcher type = JAVA_TYPE.matcher(line);
        if (type.find()) {
            currentType = type.group(2);
            current = currentType;
            return current;
        }
        Matcher member = JAVA_MEMBER.matcher(line);
        if (member.find()) {
            String name = member.group(1);
            // Filter out control keywords that structurally look like a method signature.
            if (!isControlKeyword(name)) {
                current = currentType.isEmpty() ? name : currentType + "." + name;
            }
        }
        return current;
    }

    public String current() {
        return current;
    }

    private static boolean isControlKeyword(String name) {
        return switch (name) {
            case "if", "for", "while", "switch", "catch", "try", "do", "else", "synchronized" -> true;
            default -> false;
        };
    }

    /**
     * Convenience for chunkers that work on a detached block of text rather than streaming lines:
     * returns the first heading/declaration found inside the block, or {@code fallback}.
     */
    public static String firstSectionIn(String block, SourceType type, String fallback) {
        SectionTracker t = new SectionTracker(type);
        for (String line : block.split("\n")) {
            String s = t.accept(line);
            if (!s.isEmpty()) {
                return s;
            }
        }
        return fallback;
    }
}
