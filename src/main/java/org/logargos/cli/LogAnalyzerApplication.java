package org.logargos.cli;

import org.logargos.filter.IgnoredErrorFilter;
import org.logargos.model.ErrorSummary;
import org.logargos.model.LogError;
import org.logargos.parser.LogParser;
import org.logargos.rules.ErrorClassifier;
import org.logargos.rules.ErrorSummarizer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;


/**
 * CLI entry point for the log analyzer.
 */
public final class LogAnalyzerApplication {
    private static final Path DEFAULT_IGNORE_CONFIG = Path.of("config", "ignored-errors.txt");
    private static final String PROPERTIES_RESOURCE = "log-analyzer.properties";
    private static final String DEFAULT_LOG_PATH_PROPERTY = "default.log.path";


    private LogAnalyzerApplication() {
    }

    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Usage: java -jar logargos.jar [log-file|log-dir]");
            System.exit(1);
        }

        Path requestedPath = args.length == 1 ? Path.of(args[0]) : resolveDefaultLogPath();

        try {
            Path logPath = requestedPath;

            // If the provided path is a directory, pick the most recent regular file inside
            if (Files.isDirectory(requestedPath)) {
                Optional<Path> maybeLatest = findMostRecentFileInDirectory(requestedPath);
                if (maybeLatest.isPresent()) {
                    logPath = maybeLatest.get();
                } else {
                    System.err.printf("No log files found in directory '%s'.%n", requestedPath);
                    System.exit(2);
                }
            }

            IgnoredErrorFilter filter = IgnoredErrorFilter.fromFile(DEFAULT_IGNORE_CONFIG);
            LogParser parser = new LogParser(new ErrorClassifier());
            List<LogError> errors = parser.parse(logPath);

            List<LogError> filteredErrors = errors.stream()
                    .filter(error -> !filter.shouldIgnore(error))
                    .toList();

            ErrorSummary summary = new ErrorSummarizer().summarize(filteredErrors);
            printSummary(summary);
        } catch (IOException ex) {
            System.err.printf("Failed to analyze log file '%s': %s%n", requestedPath, ex.getMessage());
            System.exit(2);
        }
    }

    private static Path resolveDefaultLogPath() {
        Properties properties = new Properties();
        try (InputStream input = LogAnalyzerApplication.class.getClassLoader().getResourceAsStream(PROPERTIES_RESOURCE)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
            // Fallback handled below.
        }

        String defaultPath = properties.getProperty(DEFAULT_LOG_PATH_PROPERTY, "C:\\comun\\logs");
        return Path.of(defaultPath);
    }

    private static Optional<Path> findMostRecentFileInDirectory(Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()));
        }
    }

    private static void printSummary(ErrorSummary summary) {
        System.out.printf("Errors detected: %d%n%n", summary.getTotalErrors());
        summary.getOccurrencesByType().forEach((type, count) ->
                System.out.printf("%s -> %d%n", type, count)
        );
    }
}