package org.logargos.cli;

import org.logargos.app.LogAnalyzerService;
import org.logargos.model.ErrorSummary;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;


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
        if (args.length > 2) {
            System.err.println("Usage: java -jar logargos.jar [--console] [log-file|log-dir]");
            System.exit(1);
        }

        boolean onlyConsole = false;
        String pathArg = null;
        for (String arg : args) {
            if ("--console".equalsIgnoreCase(arg) || "--only-console".equalsIgnoreCase(arg)) {
                onlyConsole = true;
            } else {
                pathArg = arg;
            }
        }

        Path requestedPath = pathArg != null ? Path.of(pathArg) : resolveDefaultLogPath();

        try {
            LogAnalyzerService service = new LogAnalyzerService();
            var result = service.analyze(requestedPath, new LogAnalyzerService.AnalysisOptions(DEFAULT_IGNORE_CONFIG, onlyConsole));
            printSummary(result.summary());
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

    private static void printSummary(ErrorSummary summary) {
        System.out.printf("Errors detected: %d%n%n", summary.getTotalErrors());
        summary.getOccurrencesByType().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .forEach(e -> System.out.printf("%s -> %d%n", e.getKey(), e.getValue()));
    }
}