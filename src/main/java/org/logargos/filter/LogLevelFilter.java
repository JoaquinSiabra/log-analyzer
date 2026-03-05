package org.logargos.filter;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects log levels (INFO/WARN/ERROR/DEBUG/TRACE) in common formats and filters lines by a selected set.
 */
public final class LogLevelFilter {

    public enum LogLevel {
        ERROR, WARN, INFO, DEBUG, TRACE
    }

    private static final Pattern BRACKET_LEVEL = Pattern.compile("\\[(ERROR|WARN|INFO|DEBUG|TRACE)]");
    private static final Pattern WORD_LEVEL = Pattern.compile("\\b(ERROR|WARN|INFO|DEBUG|TRACE)\\b");

    private final EnumSet<LogLevel> enabled;

    public LogLevelFilter(EnumSet<LogLevel> enabled) {
        this.enabled = EnumSet.copyOf(Objects.requireNonNull(enabled, "enabled"));
    }

    public EnumSet<LogLevel> enabled() {
        return EnumSet.copyOf(enabled);
    }

    /**
     * Returns true when the line contains a level token and that token is enabled.
     * If the line does not contain a recognised level token, it is considered not matching.
     */
    public boolean matches(String line) {
        Optional<LogLevel> level = detectLevel(line);
        return level.filter(enabled::contains).isPresent();
    }

    public static Optional<LogLevel> detectLevel(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        Matcher m = BRACKET_LEVEL.matcher(line);
        if (m.find()) {
            return Optional.of(parse(m.group(1)));
        }

        m = WORD_LEVEL.matcher(line);
        if (m.find()) {
            return Optional.of(parse(m.group(1)));
        }

        return Optional.empty();
    }

    private static LogLevel parse(String token) {
        return LogLevel.valueOf(token.toUpperCase(Locale.ROOT));
    }
}
