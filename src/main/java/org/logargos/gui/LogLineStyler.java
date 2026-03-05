package org.logargos.gui;

import org.logargos.filter.ConsoleLineFilter;

import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.Color;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies styled segments to log lines for better readability (timestamp, console type, level, etc.).
 *
 * Designed for use with a JTextPane / StyledDocument.
 */
public final class LogLineStyler {

    // Timestamp heuristic aligned with LogParser
    private static final Pattern TIMESTAMP_AT_START = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:[,.]\\d{3})?)");

    // Typical channel token: " - consoleRest - "
    private static final Pattern CHANNEL_TOKEN = Pattern.compile("\\-\\s*([A-Za-z][A-Za-z0-9_]*)\\s*\\-");

    // Levels may appear as [ERROR] or plain ERROR
    private static final Pattern LEVEL_TOKEN = Pattern.compile("\\b(ERROR|WARN|INFO|DEBUG|TRACE)\\b");
    private static final Pattern BRACKET_LEVEL_TOKEN = Pattern.compile("\\[(ERROR|WARN|INFO|DEBUG|TRACE)]");

    private static final Color TS = new Color(0x6B7280);
    private static final Color MSG = new Color(0x111827);
    private static final Color LOGGER = new Color(0x4B5563);
    private static final Color STACK = new Color(0x6B7280);
    private static final Color XMLJSON = new Color(0x0F766E);

    private static final Color CONSOLE = new Color(0x2563EB);
    private static final Color CONSOLE_REST = new Color(0x7C3AED);
    private static final Color CONSOLE_NOTIF = new Color(0x059669);
    private static final Color OTHER_CHANNEL = new Color(0x374151);

    private static final Color ERROR = new Color(0xDC2626);
    private static final Color WARN = new Color(0xD97706);

    private LogLineStyler() {
    }

    public static AttributeSet styleForPlainText() {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, MSG);
        return s;
    }

    public static AttributeSet styleForTimestamp() {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, TS);
        StyleConstants.setItalic(s, true);
        return s;
    }

    public static AttributeSet styleForConsoleType(String type) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setBold(s, true);

        if (type == null) {
            StyleConstants.setForeground(s, OTHER_CHANNEL);
            return s;
        }

        String t = type.toLowerCase(Locale.ROOT);
        if (t.equals("console")) {
            StyleConstants.setForeground(s, CONSOLE);
        } else if (t.equals("consolerest")) {
            StyleConstants.setForeground(s, CONSOLE_REST);
        } else if (t.equals("consolenotif")) {
            StyleConstants.setForeground(s, CONSOLE_NOTIF);
        } else {
            StyleConstants.setForeground(s, OTHER_CHANNEL);
        }
        return s;
    }

    public static AttributeSet styleForLevel(String level) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setBold(s, true);

        if (level == null) {
            StyleConstants.setForeground(s, MSG);
            return s;
        }

        String l = level.toUpperCase(Locale.ROOT);
        switch (l) {
            case "ERROR" -> StyleConstants.setForeground(s, ERROR);
            case "WARN" -> StyleConstants.setForeground(s, WARN);
            default -> StyleConstants.setForeground(s, MSG);
        }
        return s;
    }

    public static AttributeSet styleForLogger() {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, LOGGER);
        return s;
    }

    public static AttributeSet styleForStackTrace() {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, STACK);
        return s;
    }

    public static AttributeSet styleForXmlJson() {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, XMLJSON);
        return s;
    }

    public static boolean looksLikeTimestampedLogLine(String line) {
        if (line == null) return false;
        return TIMESTAMP_AT_START.matcher(line).find();
    }

    public static boolean looksLikeStackContinuation(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        return line.matches("^\\s+.*")
                || trimmed.toLowerCase(Locale.ROOT).startsWith("caused by:")
                || trimmed.startsWith("at ")
                || trimmed.startsWith("... ");
    }

    public static boolean looksLikeXmlOrJson(String line) {
        if (line == null) return false;
        String t = line.trim();
        return (t.startsWith("{") && t.endsWith("}"))
                || (t.startsWith("[") && t.endsWith("]"))
                || (t.startsWith("<") && t.contains(">"));
    }

    public static StyledSegments styleLine(String line) {
        if (line == null) {
            return StyledSegments.single("", styleForPlainText());
        }

        // Stack trace lines and xml/json blocks get special handling
        if (looksLikeStackContinuation(line)) {
            return StyledSegments.single(line, styleForStackTrace());
        }
        if (looksLikeXmlOrJson(line)) {
            return StyledSegments.single(line, styleForXmlJson());
        }

        // Attempt to segment: [timestamp] [rest]
        Matcher ts = TIMESTAMP_AT_START.matcher(line);
        int idx = 0;

        StyledSegments segs = new StyledSegments();

        if (ts.find() && ts.start() == 0) {
            String tsText = ts.group(1);
            segs.add(tsText, styleForTimestamp());
            idx = tsText.length();
        }

        String remaining = line.substring(idx);

        // Channel token: find first "- token -" after timestamp
        Matcher ch = CHANNEL_TOKEN.matcher(remaining);
        if (ch.find()) {
            // include everything before first dash-token as plain
            String pre = remaining.substring(0, ch.start());
            if (!pre.isEmpty()) {
                segs.add(pre, styleForPlainText());
            }

            // include the exact matched delimiter leading dash and spaces as plain,
            // and the channel token highlighted.
            String match = ch.group(0);
            String token = ch.group(1);
            int tokenStartInMatch = match.toLowerCase(Locale.ROOT).indexOf(token.toLowerCase(Locale.ROOT));
            if (tokenStartInMatch < 0) {
                segs.add(match, styleForPlainText());
            } else {
                segs.add(match.substring(0, tokenStartInMatch), styleForPlainText());
                segs.add(token, styleForConsoleType(token));
                segs.add(match.substring(tokenStartInMatch + token.length()), styleForPlainText());
            }

            String after = remaining.substring(ch.end());

            // Level token (prefer bracketed)
            LevelMatch lm = findLevel(after);
            if (lm != null) {
                if (lm.start() > 0) {
                    segs.add(after.substring(0, lm.start()), styleForPlainText());
                }
                segs.add(after.substring(lm.start(), lm.end()), styleForLevel(lm.level));
                segs.add(after.substring(lm.end()), styleForPlainText());
            } else {
                segs.add(after, styleForPlainText());
            }

            return segs;
        }

        // No channel format detected; just style levels if present.
        LevelMatch lm = findLevel(remaining);
        if (lm != null) {
            if (lm.start() > 0) {
                segs.add(remaining.substring(0, lm.start()), styleForPlainText());
            }
            segs.add(remaining.substring(lm.start(), lm.end()), styleForLevel(lm.level));
            segs.add(remaining.substring(lm.end()), styleForPlainText());
        } else {
            segs.add(remaining, styleForPlainText());
        }

        return segs;
    }

    private static LevelMatch findLevel(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        Matcher m = BRACKET_LEVEL_TOKEN.matcher(text);
        if (m.find()) {
            return new LevelMatch(m.start(), m.end(), m.group(1));
        }

        m = LEVEL_TOKEN.matcher(text);
        if (m.find()) {
            return new LevelMatch(m.start(), m.end(), m.group(1));
        }

        return null;
    }

    private record LevelMatch(int start, int end, String level) {
    }

    /**
     * Helper container for styled segments.
     */
    public static final class StyledSegments {
        private final java.util.List<String> texts = new java.util.ArrayList<>();
        private final java.util.List<AttributeSet> styles = new java.util.ArrayList<>();

        public static StyledSegments single(String text, AttributeSet style) {
            StyledSegments segs = new StyledSegments();
            segs.add(text, style);
            return segs;
        }

        public void add(String text, AttributeSet style) {
            texts.add(text);
            styles.add(style);
        }

        public int size() {
            return texts.size();
        }

        public String text(int i) {
            return texts.get(i);
        }

        public AttributeSet style(int i) {
            return styles.get(i);
        }
    }
}
