package org.logargos.filter;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Matches log lines produced by "console" appenders, typically containing fragments like:
 * " - console - ", " - consoleRest - ", etc.
 */
public final class ConsoleLineFilter {

    public enum ConsoleType {
        ANY("console"),
        CONSOLE("console"),
        CONSOLE_REST("consoleRest"),
        CONSOLE_NOTIF("consoleNotif");

        private final String token;

        ConsoleType(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        Pattern toPattern() {
            if (this == ANY) {
                return DEFAULT_PATTERN;
            }
            // Match exact dash-delimited token: "- consoleRest -" with optional spaces
            return Pattern.compile("(?i).*\\-\\s*" + Pattern.quote(token) + "\\s*\\-.*");
        }

        public static ConsoleType fromToken(String token) {
            if (token == null || token.isBlank()) {
                return ANY;
            }
            String t = token.trim();
            // Accept both canonical tokens ("consoleRest") and lowercase keys stored in prefs ("consolerest").
            String lower = t.toLowerCase(Locale.ROOT);
            return switch (lower) {
                case "console" -> CONSOLE;
                case "consolerest" -> CONSOLE_REST;
                case "consolenotif" -> CONSOLE_NOTIF;
                case "any" -> ANY;
                default -> ANY;
            };
        }
    }

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

    public ConsoleLineFilter(ConsoleType type) {
        this(Objects.requireNonNull(type, "type").toPattern());
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