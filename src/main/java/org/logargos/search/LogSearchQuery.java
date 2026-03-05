package org.logargos.search;

import java.util.Locale;
import java.util.Objects;

public final class LogSearchQuery {

    public enum Mode {
        CONTAINS,
        REGEX
    }

    private final String raw;
    private final Mode mode;
    private final boolean caseSensitive;

    public LogSearchQuery(String raw, Mode mode, boolean caseSensitive) {
        this.raw = Objects.requireNonNullElse(raw, "");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.caseSensitive = caseSensitive;
    }

    public String raw() {
        return raw;
    }

    public Mode mode() {
        return mode;
    }

    public boolean caseSensitive() {
        return caseSensitive;
    }

    public boolean isEmpty() {
        return raw.isBlank();
    }

    public String normalizedNeedle() {
        return caseSensitive ? raw : raw.toLowerCase(Locale.ROOT);
    }
}
