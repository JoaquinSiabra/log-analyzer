package org.logargos.search;

import org.logargos.filter.ConsoleLineFilter;
import org.logargos.filter.LogLevelFilter;

import java.io.BufferedReader;
import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * Stateful streaming search session. Keeps a cursor (reader position + event index)
 * so repeated "Find next" doesn't restart scanning from the beginning.
 */
public final class LogSearchSession implements Closeable {

    public record Options(
            ConsoleLineFilter consoleFilter,
            LogLevelFilter levelFilter,
            boolean includeTraceLines
    ) {
        public Options {
            Objects.requireNonNull(consoleFilter, "consoleFilter");
            Objects.requireNonNull(levelFilter, "levelFilter");
        }
    }

    private final Path file;
    private final LogSearchQuery query;
    private final Options options;

    private final String needle;
    private final Pattern regex;

    private BufferedReader reader;
    private long currentEventIndex = 0;

    public LogSearchSession(Path file, LogSearchQuery query, Options options) {
        this.file = Objects.requireNonNull(file, "file");
        this.query = Objects.requireNonNull(query, "query");
        this.options = Objects.requireNonNull(options, "options");

        this.needle = query.caseSensitive() ? query.raw() : query.raw().toLowerCase(Locale.ROOT);
        this.regex = query.mode() == LogSearchQuery.Mode.REGEX
                ? Pattern.compile(query.raw(), query.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE)
                : null;
    }

    public void open() throws Exception {
        if (reader != null) {
            return;
        }
        reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
        currentEventIndex = 0;
    }

    public OptionalLong findNextEventIndex() throws Exception {
        if (query.isEmpty()) {
            return OptionalLong.empty();
        }
        if (reader == null) {
            open();
        }

        String line;
        while ((line = reader.readLine()) != null) {
            if (!options.consoleFilter().matches(line)) {
                continue;
            }
            if (!options.levelFilter().enabled().isEmpty() && !options.levelFilter().matches(line)) {
                continue;
            }

            boolean match = matches(line);

            if (!match && options.includeTraceLines()) {
                // scan trace lines
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
                    if (matches(next)) {
                        match = true;
                    }
                    reader.mark(256 * 1024);
                }
            }

            long idx = currentEventIndex;
            currentEventIndex++;

            if (match) {
                return OptionalLong.of(idx);
            }
        }

        return OptionalLong.empty();
    }

    private boolean matches(String line) {
        return switch (query.mode()) {
            case CONTAINS -> {
                if (query.caseSensitive()) {
                    yield line.contains(needle);
                }
                yield line.toLowerCase(Locale.ROOT).contains(needle);
            }
            case REGEX -> regex.matcher(line).find();
        };
    }

    @Override
    public void close() {
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ignored) {
            } finally {
                reader = null;
            }
        }
    }
}
