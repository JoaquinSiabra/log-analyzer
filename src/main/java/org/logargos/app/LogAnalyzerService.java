package org.logargos.app;

import org.logargos.filter.ConsoleLineFilter;
import org.logargos.filter.IgnoredErrorFilter;
import org.logargos.model.ErrorSummary;
import org.logargos.model.LogError;
import org.logargos.parser.LogParser;
import org.logargos.rules.ErrorClassifier;
import org.logargos.rules.ErrorSummarizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates log analysis (path resolution, optional pre-filters, ignored-pattern filtering, and summary).
 */
public final class LogAnalyzerService {

    public record AnalysisOptions(
            Path ignoredErrorsConfig,
            boolean onlyConsoleLines
    ) {
        public static AnalysisOptions defaults(Path ignoredErrorsConfig) {
            return new AnalysisOptions(ignoredErrorsConfig, false);
        }
    }

    public record AnalysisResult(
            Path analyzedFile,
            List<LogError> allDetectedErrors,
            List<LogError> errorsAfterIgnoreFilter,
            ErrorSummary summary
    ) {
    }

    private final LogParser parser;
    private final ErrorSummarizer summarizer;
    private final ConsoleLineFilter consoleLineFilter;

    public LogAnalyzerService() {
        this(new LogParser(new ErrorClassifier()), new ErrorSummarizer(), new ConsoleLineFilter());
    }

    public LogAnalyzerService(LogParser parser, ErrorSummarizer summarizer, ConsoleLineFilter consoleLineFilter) {
        this.parser = parser;
        this.summarizer = summarizer;
        this.consoleLineFilter = consoleLineFilter;
    }

    /**
     * Analyze either a log file, or a directory (by picking the most recent regular file inside).
     */
    public AnalysisResult analyze(Path requestedPath, AnalysisOptions options) throws IOException {
        if (requestedPath == null) {
            throw new IllegalArgumentException("requestedPath must not be null");
        }
        if (options == null) {
            throw new IllegalArgumentException("options must not be null");
        }

        Path logFile = resolveLogFile(requestedPath)
                .orElseThrow(() -> new IOException("No log files found in directory '" + requestedPath + "'."));

        Path fileToParse = logFile;
        if (options.onlyConsoleLines()) {
            fileToParse = createConsoleOnlyTempFile(logFile);
        }

        IgnoredErrorFilter ignored = IgnoredErrorFilter.fromFile(options.ignoredErrorsConfig());

        List<LogError> errors = parser.parse(fileToParse);
        List<LogError> filteredErrors = errors.stream()
                .filter(e -> !ignored.shouldIgnore(e))
                .toList();

        ErrorSummary summary = summarizer.summarize(filteredErrors);
        return new AnalysisResult(logFile, errors, filteredErrors, summary);
    }

    public Optional<Path> resolveLogFile(Path requestedPath) throws IOException {
        if (Files.isDirectory(requestedPath)) {
            return findMostRecentFileInDirectory(requestedPath);
        }
        return Files.exists(requestedPath) ? Optional.of(requestedPath) : Optional.empty();
    }

    private Optional<Path> findMostRecentFileInDirectory(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()));
        }
    }

    private Path createConsoleOnlyTempFile(Path logFile) throws IOException {
        Path tmp = Files.createTempFile("logargos_console_only_", ".log");
        tmp.toFile().deleteOnExit();

        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            List<String> out = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (consoleLineFilter.matches(line)) {
                    out.add(line);
                }
            }
            Files.write(tmp, out, StandardCharsets.UTF_8);
        }

        return tmp;
    }
}
