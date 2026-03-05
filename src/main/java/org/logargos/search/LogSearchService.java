package org.logargos.search;

import org.logargos.filter.ConsoleLineFilter;
import org.logargos.filter.LogLevelFilter;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * Streaming search over a full log file without loading it in memory.
 *
 * Result is the "event index" (0-based) of the first matching header event, where event indices
 * are counted only among lines matching the current filters (console type + log level).
 */
public final class LogSearchService {

    public record SearchOptions(
            ConsoleLineFilter consoleFilter,
            LogLevelFilter levelFilter,
            boolean includeTraceLines
    ) {
        public SearchOptions {
            Objects.requireNonNull(consoleFilter, "consoleFilter");
            Objects.requireNonNull(levelFilter, "levelFilter");
        }
    }

    public OptionalLong findFirstMatchingEventIndex(Path file,
                                                    LogSearchQuery query,
                                                    SearchOptions options) throws Exception {
        if (query == null || query.isEmpty()) {
            return OptionalLong.empty();
        }

        final String needle = query.normalizedNeedle();
        final Pattern regex = query.mode() == LogSearchQuery.Mode.REGEX
                ? Pattern.compile(query.raw(), query.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE)
                : null;

        long eventIndex = 0;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!options.consoleFilter().matches(line)) {
                    continue;
                }
                if (!options.levelFilter().enabled().isEmpty() && !options.levelFilter().matches(line)) {
                    continue;
                }

                boolean match = matches(line, needle, regex, query);

                if (!match && options.includeTraceLines()) {
                    // Look ahead into trace lines, but without advancing eventIndex.
                    reader.mark(256 * 1024);
                    while (true) {
                        String next = reader.readLine();
                        if (next == null) {
                            break;
                        }
                        if (org.logargos.gui.LogLineStyler.looksLikeTimestampedLogLine(next)) {
                            reader.reset();
                            break;
                        }
                        if (matches(next, needle, regex, query)) {
                            match = true;
                            // keep reading until we reset below
                            // (we already consumed trace lines; that's fine)
                        }
                        reader.mark(256 * 1024);
                    }
                }

                if (match) {
                    return OptionalLong.of(eventIndex);
                }

                eventIndex++;
            }
        }

        return OptionalLong.empty();
    }

    private static boolean matches(String line,
                                  String needle,
                                  Pattern regex,
                                  LogSearchQuery query) {
        if (line == null) {
            return false;
        }
        return switch (query.mode()) {
            case CONTAINS -> {
                if (query.caseSensitive()) {
                    yield line.contains(needle);
                }
                yield line.toLowerCase(java.util.Locale.ROOT).contains(needle);
            }
            case REGEX -> regex.matcher(line).find();
        };
    }
}
