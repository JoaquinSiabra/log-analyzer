package org.logargos.cli;

import org.logargos.filter.IgnoredErrorFilter;
import org.logargos.model.ErrorSummary;
import org.logargos.model.LogError;
import org.logargos.parser.LogParser;
import org.logargos.rules.ErrorClassifier;
import org.logargos.rules.ErrorSummarizer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI entry point for the log analyzer.
 */
public final class LogAnalyzerApplication {
    private static final Path DEFAULT_IGNORE_CONFIG = Path.of("config", "ignored-errors.txt");

    private LogAnalyzerApplication() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar logargos.jar <log-file>");
            System.exit(1);
        }

        Path logPath = Path.of(args[0]);

        try {
            IgnoredErrorFilter filter = IgnoredErrorFilter.fromFile(DEFAULT_IGNORE_CONFIG);
            LogParser parser = new LogParser(new ErrorClassifier());
            List<LogError> errors = parser.parse(logPath);

            List<LogError> filteredErrors = errors.stream()
                    .filter(error -> !filter.shouldIgnore(error))
                    .toList();

            ErrorSummary summary = new ErrorSummarizer().summarize(filteredErrors);
            printSummary(summary);
        } catch (IOException ex) {
            System.err.printf("Failed to analyze log file '%s': %s%n", logPath, ex.getMessage());
            System.exit(2);
        }
    }

    private static void printSummary(ErrorSummary summary) {
        System.out.printf("Errors detected: %d%n%n", summary.getTotalErrors());
        summary.getOccurrencesByType().forEach((type, count) ->
                System.out.printf("%s -> %d%n", type, count)
        );
    }
}
