package org.logargos.filter;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Matches log lines produced by "console" appenders, typically containing fragments like:
 * " - console - ", " - consoleRest - ", etc.
 */
public final class ConsoleLineFilter {

    /**
     * Default heuristic: look for a dash-delimited token that starts with "console".
     * Example matches:
     * - "2026-... - console - (...)"
     * - "2026-... - consoleRest - (...)"
     */
    public static final Pattern DEFAULT_PATTERN = Pattern.compile("(?i).*\\-\\s*console\\w*\\s*\\-.*");

    private final Pattern pattern;

    public ConsoleLineFilter() {
        this(DEFAULT_PATTERN);
    }

    public ConsoleLineFilter(Pattern pattern) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
    }

    public boolean matches(String line) {
        if (line == null) {
            return false;
        }
        return pattern.matcher(line).matches();
    }
}
