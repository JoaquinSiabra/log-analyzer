package org.logargos.rules;

import org.logargos.model.ErrorSummary;
import org.logargos.model.LogError;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Groups and counts errors by exception type.
 */
public class ErrorSummarizer {

    public ErrorSummary summarize(List<LogError> errors) {
        Map<String, Long> grouped = errors.stream()
                .collect(Collectors.groupingBy(LogError::getExceptionType, Collectors.counting()));

        Map<String, Long> sorted = grouped.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        return new ErrorSummary(errors.size(), sorted);
    }
}
