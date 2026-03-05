package org.logargos.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConsoleLineFilterTest {

    @Test
    void matches_consoleToken() {
        ConsoleLineFilter filter = new ConsoleLineFilter();
        assertTrue(filter.matches("2026-03-04T10:32:41.624 - console - ( -  - ) Foo ERROR - boom"));
    }

    @Test
    void matches_consoleRestToken() {
        ConsoleLineFilter filter = new ConsoleLineFilter();
        assertTrue(filter.matches("2026-03-04T10:32:41.851 - consoleRest - ( -  - ) Foo ERROR - boom"));
    }

    @Test
    void doesNotMatch_nonConsole() {
        ConsoleLineFilter filter = new ConsoleLineFilter();
        assertFalse(filter.matches("10:32:41.658 [HotRod-client-async-pool-1-2] WARN  org.infinispan.HOTROD - ..."));
    }
}
