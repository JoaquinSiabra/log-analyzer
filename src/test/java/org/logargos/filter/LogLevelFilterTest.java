package org.logargos.filter;

import org.junit.jupiter.api.Test;
import org.logargos.filter.LogLevelFilter.LogLevel;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogLevelFilterTest {

    @Test
    void matches_bracketLevel() {
        LogLevelFilter f = new LogLevelFilter(EnumSet.of(LogLevel.ERROR));
        assertTrue(f.matches("2026-03-04T10:00:00.000 - console - (...) [ERROR] something"));
        assertFalse(f.matches("2026-03-04T10:00:00.000 - console - (...) [WARN] something"));
    }

    @Test
    void matches_wordLevel() {
        LogLevelFilter f = new LogLevelFilter(EnumSet.of(LogLevel.WARN));
        assertTrue(f.matches("2026-03-04T10:00:00.000 - console - (...) o.a.b.C WARN  - message"));
        assertFalse(f.matches("2026-03-04T10:00:00.000 - console - (...) o.a.b.C INFO  - message"));
    }

    @Test
    void noLevel_noMatch() {
        LogLevelFilter f = new LogLevelFilter(EnumSet.of(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR));
        assertFalse(f.matches("[ADVERTENCIA] J2CA8501E: ..."));
    }
}
