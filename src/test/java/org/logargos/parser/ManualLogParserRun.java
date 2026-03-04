package org.logargos.parser;

import org.logargos.model.LogError;
import org.logargos.rules.ErrorClassifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ManualLogParserRun {
    public static void main(String[] args) throws Exception {
        String content = String.join("\n",
                "2026-03-04 12:00:00 ERROR Something failed",
                "\tat com.example.Foo.bar(Foo.java:42)",
                "Caused by: java.lang.IllegalStateException: bad state",
                "\tat com.example.Bar.baz(Bar.java:27)",
                ""
        );

        Path tmp = Files.createTempFile("logparser_manual", ".log");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);

        LogParser parser = new LogParser(new ErrorClassifier());
        List<LogError> errors = parser.parse(tmp);

        System.out.println("Parsed errors count: " + errors.size());
        for (LogError e : errors) {
            System.out.println("Type: " + e.getExceptionType());
            System.out.println("Message: " + e.getOriginalMessage());
            System.out.println("Stack lines:");
            e.getStackTrace().forEach(System.out::println);
        }
    }
}
