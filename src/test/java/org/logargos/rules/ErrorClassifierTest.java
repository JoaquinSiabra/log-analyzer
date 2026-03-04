package org.logargos.rules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ErrorClassifierTest {
    private final ErrorClassifier classifier = new ErrorClassifier();

    @Test
    public void extractExceptionType_foundOnLine() {
        String line = "2026-03-04 12:00:00 ERROR java.lang.NullPointerException at com.example.Foo.bar(Foo.java:42)";
        assertEquals("NullPointerException", classifier.extractExceptionType(line));
    }

    @Test
    public void findExceptionInLines_causedBy() {
        List<String> lines = List.of(
                "\tat com.example.Foo.bar(Foo.java:42)",
                "Caused by: java.lang.IllegalStateException: bad state",
                "\tat com.example.Bar.baz(Bar.java:27)"
        );

        assertEquals("IllegalStateException", classifier.findExceptionInLines(lines));
    }

    @Test
    public void findExceptionInLines_fallbackAnyLine() {
        List<String> lines = List.of(
                "\tat com.example.Foo.bar(Foo.java:42)",
                "some text with SQLException somewhere",
                "\tat com.example.Bar.baz(Bar.java:27)"
        );

        assertEquals("SQLException", classifier.findExceptionInLines(lines));
    }

    @Test
    public void findExceptionInLines_none() {
        List<String> lines = List.of(
                "\tat com.example.Foo.bar(Foo.java:42)",
                "\tat com.example.Bar.baz(Bar.java:27)"
        );

        assertEquals("UnknownError", classifier.findExceptionInLines(lines));
    }
}