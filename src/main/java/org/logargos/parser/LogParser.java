package org.logargos.parser;

import org.logargos.model.LogError;
import org.logargos.rules.ErrorClassifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Efficient line-by-line parser that collapses stack traces into a single event.
 */
public class LogParser {
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:[,.]\\d{3})?).*");

    private final ErrorClassifier classifier;

    public LogParser(ErrorClassifier classifier) {
        this.classifier = classifier;
    }

    public List<LogError> parse(Path logFile) throws IOException {
        List<LogError> errors = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String pendingLine = null;

            while (true) {
                String line = pendingLine != null ? pendingLine : reader.readLine();
                pendingLine = null;

                if (line == null) {
                    break;
                }

                String trimmedLine = line.trim();
                // Skip lines that are clearly stack-trace continuations so they don't get
                // treated as separate error headers (e.g. indented lines, 'at ...', 'Caused by: ...').
                if (line.matches("^\\s+.*") || trimmedLine.toLowerCase().startsWith("caused by:") || trimmedLine.startsWith("at ")) {
                    // ignore stray stack trace lines that may appear without a leading header
                    continue;
                }

                if (!classifier.isErrorLine(line)) {
                    continue;
                }

                String timestamp = extractTimestamp(line);
                String exceptionType = classifier.extractExceptionType(line);
                List<String> stackTraceLines = new ArrayList<>();

                while (true) {
                    String nextLine = reader.readLine();
                    if (nextLine == null) {
                        break;
                    }

                    String trimmed = nextLine.trim();
                    // Consider indented lines, 'Caused by:' lines, and 'at ' lines as part of the stack trace
                    if (nextLine.matches("^\\s+.*") || trimmed.toLowerCase().startsWith("caused by:") || trimmed.startsWith("at ")) {
                        stackTraceLines.add(nextLine);
                    } else {
                        pendingLine = nextLine;
                        break;
                    }
                }

                // If exception type wasn't found on the header line, try to find it in the stack trace
                if ("UnknownError".equals(exceptionType)) {
                    String found = classifier.findExceptionInLines(stackTraceLines);
                    if (!"UnknownError".equals(found)) {
                        exceptionType = found;
                    }
                }

                errors.add(new LogError(timestamp, exceptionType, line, stackTraceLines));
            }
        }

        return errors;
    }

    private String extractTimestamp(String line) {
        Matcher matcher = TIMESTAMP_PATTERN.matcher(line);
        return matcher.matches() ? matcher.group(1) : null;
    }
}