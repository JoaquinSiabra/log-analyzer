package org.logargos.rules;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encapsulates error detection and exception type extraction rules.
 */
public class ErrorClassifier {
    private static final Pattern ERROR_LINE_PATTERN = Pattern.compile("(?i).*(ERROR|Exception|Stacktrace).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile("\\b([A-Za-z_$][A-Za-z\\d_$]*(?:Exception|Error))\\b");

    public boolean isErrorLine(String line) {
        return line != null && ERROR_LINE_PATTERN.matcher(line).matches();
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
}
