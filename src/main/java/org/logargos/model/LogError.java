package org.logargos.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents one detected error event in the log.
 */
public final class LogError {
    private final String timestamp;
    private final String exceptionType;
    private final String originalMessage;
    private final List<String> stackTrace;

    public LogError(String timestamp, String exceptionType, String originalMessage, List<String> stackTrace) {
        this.timestamp = timestamp;
        this.exceptionType = Objects.requireNonNullElse(exceptionType, "UnknownError");
        this.originalMessage = Objects.requireNonNullElse(originalMessage, "");
        this.stackTrace = List.copyOf(Objects.requireNonNullElse(stackTrace, List.of()));
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public List<String> getStackTrace() {
        return stackTrace;
    }

    public String asFullText() {
        if (stackTrace.isEmpty()) {
            return originalMessage;
        }

        return originalMessage + System.lineSeparator() + String.join(System.lineSeparator(), stackTrace);
    }
}
