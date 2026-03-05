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

public class LogSearchServiceTest {

    @Test
    void findsFirstMatchingEventIndex_streaming() throws Exception {
        Path tmp = Files.createTempFile("logsearch", ".log");
        Files.writeString(tmp, String.join(System.lineSeparator(),
                "2026-03-04T10:00:00.000 - consoleRest - ( -  - ) [INFO] a",
                "2026-03-04T10:00:01.000 - consoleRest - ( -  - ) [ERROR] first boom",
                "\tat com.x.Y",
                "2026-03-04T10:00:02.000 - consoleRest - ( -  - ) [ERROR] second boom",
                ""
        ));

        LogSearchService svc = new LogSearchService();
        var q = new LogSearchQuery("boom", LogSearchQuery.Mode.CONTAINS, false);
        var opts = new LogSearchService.SearchOptions(
                new ConsoleLineFilter(ConsoleLineFilter.ConsoleType.CONSOLE_REST),
                new LogLevelFilter(EnumSet.of(LogLevel.ERROR)),
                true
        );

        var idx = svc.findFirstMatchingEventIndex(tmp, q, opts);
        assertTrue(idx.isPresent());
        assertEquals(0L, idx.getAsLong());
    }
}
