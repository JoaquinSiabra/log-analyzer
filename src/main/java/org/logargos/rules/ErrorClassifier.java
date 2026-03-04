package org.logargos.rules;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encapsulates error detection and exception type extraction rules.
 */
public class ErrorClassifier {
    private static final Pattern ERROR_LINE_PATTERN = Pattern.compile("(?i).*(ERROR|Exception|Stacktrace).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile("\\b([A-Za-z_$][A-Za-z\\d_$]*(?:Exception|Error))\\b");

    public boolean isErrorLine(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        // Avoid matching stack-trace continuation lines as errors
        if (line.matches("^\\s+.*") || trimmed.toLowerCase().startsWith("caused by:") || trimmed.startsWith("at ")) {
            return false;
        }
        return ERROR_LINE_PATTERN.matcher(line).matches();
    }

    public String extractExceptionType(String line) {
        if (line == null || line.isBlank()) {
            return "UnknownError";
        }

        Matcher matcher = EXCEPTION_PATTERN.matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "UnknownError";
    }

    /**
     * Search a list of lines (for example the stacked lines after an error header)
     * for a recognisable exception type. This looks for explicit "Caused by:" lines
     * first, then falls back to scanning any line for a token that matches the
     * EXCEPTION_PATTERN. Returns "UnknownError" when none found.
     */
    public String findExceptionInLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "UnknownError";
        }

        // First look for typical 'Caused by:' lines which often contain the full type
        for (String l : lines) {
            if (l == null) continue;
            String lower = l.toLowerCase();
            if (lower.contains("caused by:")) {
                Matcher m = EXCEPTION_PATTERN.matcher(l);
                if (m.find()) {
                    return m.group(1);
                }
            }
        }

        // Fallback: scan any line for an exception-like token
        for (String l : lines) {
            if (l == null) continue;
            Matcher m = EXCEPTION_PATTERN.matcher(l);
            if (m.find()) {
                return m.group(1);
            }
        }

        return "UnknownError";
    }
}