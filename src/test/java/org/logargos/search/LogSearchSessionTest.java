package org.logargos.search;

import org.junit.jupiter.api.Test;
import org.logargos.filter.ConsoleLineFilter;
import org.logargos.filter.LogLevelFilter;
import org.logargos.filter.LogLevelFilter.LogLevel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogSearchSessionTest {

    @Test
    void findNextContinuesWithoutRestart() throws Exception {
        Path tmp = Files.createTempFile("logsearch-session", ".log");
        Files.writeString(tmp, String.join(System.lineSeparator(),
                "2026-03-04T10:00:00.000 - consoleRest - ( -  - ) [ERROR] boom1",
                "2026-03-04T10:00:01.000 - consoleRest - ( -  - ) [ERROR] boom2",
                "2026-03-04T10:00:02.000 - consoleRest - ( -  - ) [ERROR] boom3"
        ));

        var q = new LogSearchQuery("boom", LogSearchQuery.Mode.CONTAINS, false);
        var opts = new LogSearchSession.Options(
                new ConsoleLineFilter(ConsoleLineFilter.ConsoleType.CONSOLE_REST),
                new LogLevelFilter(EnumSet.of(LogLevel.ERROR)),
                false
        );

        try (var session = new LogSearchSession(tmp, q, opts)) {
            var a = session.findNextEventIndex();
            assertTrue(a.isPresent());
            assertEquals(0L, a.getAsLong());

            var b = session.findNextEventIndex();
            assertTrue(b.isPresent());
            assertEquals(1L, b.getAsLong());
        }
    }
}
