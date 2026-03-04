package org.logargos.parser;

import org.junit.jupiter.api.Test;
import org.logargos.model.LogError;
import org.logargos.rules.ErrorClassifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LogParserTest {

    @Test
    public void parse_headerContainsType() throws IOException {
        String content = "2026-03-04 12:00:00 ERROR java.lang.NullPointerException at com.example.Foo.bar(Foo.java:42)\n";
        Path tmp = Files.createTempFile("logparser", ".log");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);

        LogParser parser = new LogParser(new ErrorClassifier());
        List<LogError> errors = parser.parse(tmp);

        assertEquals(1, errors.size());
        assertEquals("NullPointerException", errors.get(0).getExceptionType());
    }

    @Test
    public void parse_typeInCausedBy() throws IOException {
        String content = String.join("\n",
                "2026-03-04 12:00:00 ERROR Something failed",
                "\tat com.example.Foo.bar(Foo.java:42)",
                "Caused by: java.lang.IllegalStateException: bad state",
                "\tat com.example.Bar.baz(Bar.java:27)",
                ""
        );

        Path tmp = Files.createTempFile("logparser", ".log");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);

        LogParser parser = new LogParser(new ErrorClassifier());
        List<LogError> errors = parser.parse(tmp);

        assertEquals(1, errors.size());
        assertEquals("IllegalStateException", errors.get(0).getExceptionType());
    }

    @Test
    public void parse_unknownWhenNoType() throws IOException {
        String content = String.join("\n",
                "2026-03-04 12:00:00 ERROR Something failed",
                "\tat com.example.Foo.bar(Foo.java:42)",
                "\tat com.example.Bar.baz(Bar.java:27)",
                ""
        );

        Path tmp = Files.createTempFile("logparser", ".log");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);

        LogParser parser = new LogParser(new ErrorClassifier());
        List<LogError> errors = parser.parse(tmp);

        assertEquals(1, errors.size());
        assertEquals("UnknownError", errors.get(0).getExceptionType());
    }
}