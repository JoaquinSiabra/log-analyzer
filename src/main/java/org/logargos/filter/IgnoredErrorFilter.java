package org.logargos.filter;

import org.logargos.model.LogError;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Filters known non-critical errors by matching configurable patterns.
 */
public class IgnoredErrorFilter {
    private final List<String> ignoredPatterns;

    private IgnoredErrorFilter(List<String> ignoredPatterns) {
        this.ignoredPatterns = ignoredPatterns;
    }

    public static IgnoredErrorFilter fromFile(Path configFile) throws IOException {
        if (configFile == null || Files.notExists(configFile)) {
            return new IgnoredErrorFilter(List.of());
        }

        try (Stream<String> lines = Files.lines(configFile, StandardCharsets.UTF_8)) {
            List<String> patterns = lines
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .toList();
            return new IgnoredErrorFilter(patterns);
        }
    }

    public boolean shouldIgnore(LogError error) {
        String source = error.asFullText().toLowerCase(Locale.ROOT);
        return ignoredPatterns.stream().anyMatch(source::contains);
    }
}
