package org.logargos.model;

import java.util.Map;

/**
 * Aggregated error report.
 */
public final class ErrorSummary {
    private final int totalErrors;
    private final Map<String, Long> occurrencesByType;

    public ErrorSummary(int totalErrors, Map<String, Long> occurrencesByType) {
        this.totalErrors = totalErrors;
        this.occurrencesByType = Map.copyOf(occurrencesByType);
    }

    public int getTotalErrors() {
        return totalErrors;
    }

    public Map<String, Long> getOccurrencesByType() {
        return occurrencesByType;
    }
}
