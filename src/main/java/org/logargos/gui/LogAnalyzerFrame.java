package org.logargos.gui;

import org.logargos.app.LogAnalyzerService;
import org.logargos.filter.ConsoleLineFilter;
import org.logargos.filter.ConsoleLineFilter.ConsoleType;
import org.logargos.filter.LogLevelFilter;
import org.logargos.filter.LogLevelFilter.LogLevel;

import javax.swing.ButtonGroup;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.prefs.Preferences;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import javax.swing.JToolBar;
import javax.swing.JLabel;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/**
 * Simple Swing UI: open a log file/directory and optionally filter to console lines.
 */
public final class LogAnalyzerFrame extends JFrame {

    private static final Path DEFAULT_IGNORE_CONFIG = Path.of("config", "ignored-errors.txt");

    private final LogAnalyzerService service = new LogAnalyzerService();

    private ConsoleType selectedConsoleType = ConsoleType.ANY;

    // Keep a filter instance so it can be reused when rendering
    private ConsoleLineFilter consoleLineFilter = new ConsoleLineFilter(selectedConsoleType);

    private final JTextPane output = new JTextPane();
    private final JCheckBoxMenuItem onlyConsoleMenuItem = new JCheckBoxMenuItem("Solo líneas console ('- console -')");
    private final JCheckBoxMenuItem showFullTraceMenuItem = new JCheckBoxMenuItem("Mostrar traza completa (stacktrace)");

    private Path currentPath;

    private static final String PREF_LAST_PATH = "lastPath";
    private static final String PREF_LAST_DIR = "lastDir";
    private static final String PREF_ONLY_CONSOLE = "onlyConsole";
    private static final String PREF_SHOW_TRACE = "showTrace";
    private static final String PREF_CONSOLE_TYPE = "consoleType";
    private static final String PREF_LEVELS = "logLevels";

    private final Preferences prefs = Preferences.userNodeForPackage(LogAnalyzerFrame.class);

    private JRadioButtonMenuItem anyTypeItem;
    private JRadioButtonMenuItem consoleTypeItem;
    private JRadioButtonMenuItem consoleRestTypeItem;
    private JRadioButtonMenuItem consoleNotifTypeItem;

    private EnumSet<LogLevel> selectedLevels = EnumSet.of(LogLevel.ERROR, LogLevel.WARN, LogLevel.INFO);
    private LogLevelFilter logLevelFilter = new LogLevelFilter(selectedLevels);

    private JCheckBoxMenuItem levelErrorItem;
    private JCheckBoxMenuItem levelWarnItem;
    private JCheckBoxMenuItem levelInfoItem;
    private JCheckBoxMenuItem levelDebugItem;
    private JCheckBoxMenuItem levelTraceItem;

    private final RenderLimits limits = RenderLimits.defaults();

    private SwingWorker<Void, String> renderWorker;

    // Debounce timer for rapid filter toggles
    private javax.swing.Timer debounceTimer;

    private final PaginationState paging = new PaginationState(2000);

    // scroll-trigger guard
    private volatile boolean autoLoadInProgress = false;

    private JScrollPane outputScrollPane;

    private volatile boolean renderInProgress = false;
    private volatile boolean ignoreScrollEvents = false;
    private long lastAutoLoadAtMs = 0;

    private enum ScrollDirection { NONE, UP, DOWN }

    private volatile ScrollDirection pendingScrollDirection = ScrollDirection.NONE;

    private volatile boolean hasMoreDown = true;

    public LogAnalyzerFrame() {
        super("LogArgos - Log Analyzer");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));

        setJMenuBar(buildMenuBar());
        setContentPane(buildContent());

        output.setEditable(false);
        output.setFont(output.getFont().deriveFont(12f));

        onlyConsoleMenuItem.addActionListener(e -> {
            persistPreferences();
            reanalyzeIfPossible();
        });
        showFullTraceMenuItem.addActionListener(e -> {
            persistPreferences();
            reanalyzeIfPossible();
        });

        debounceTimer = new javax.swing.Timer(250, e -> reanalyzeIfPossibleImmediate());
        debounceTimer.setRepeats(false);

        // Restore last configuration
        restorePreferences();

        // Hook scroll autoload after components exist
        installAutoScrollLoading();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("Fichero");
        JMenuItem openFile = new JMenuItem("Abrir archivo...");
        JMenuItem openDir = new JMenuItem("Abrir directorio...");
        JMenuItem exit = new JMenuItem("Salir");

        openFile.addActionListener(e -> chooseAndAnalyze(false));
        openDir.addActionListener(e -> chooseAndAnalyze(true));
        exit.addActionListener(e -> dispose());

        fileMenu.add(openFile);
        fileMenu.add(openDir);
        fileMenu.addSeparator();
        fileMenu.add(exit);

        JMenu filterMenu = new JMenu("Filtro");
        filterMenu.add(onlyConsoleMenuItem);
        filterMenu.add(showFullTraceMenuItem);
        filterMenu.addSeparator();

        // Levels section
        levelErrorItem = new JCheckBoxMenuItem("Nivel: ERROR", true);
        levelWarnItem = new JCheckBoxMenuItem("Nivel: WARN", true);
        levelInfoItem = new JCheckBoxMenuItem("Nivel: INFO", true);
        levelDebugItem = new JCheckBoxMenuItem("Nivel: DEBUG", false);
        levelTraceItem = new JCheckBoxMenuItem("Nivel: TRACE", false);

        levelErrorItem.addActionListener(e -> onLevelsChanged());
        levelWarnItem.addActionListener(e -> onLevelsChanged());
        levelInfoItem.addActionListener(e -> onLevelsChanged());
        levelDebugItem.addActionListener(e -> onLevelsChanged());
        levelTraceItem.addActionListener(e -> onLevelsChanged());

        filterMenu.add(levelErrorItem);
        filterMenu.add(levelWarnItem);
        filterMenu.add(levelInfoItem);
        filterMenu.add(levelDebugItem);
        filterMenu.add(levelTraceItem);
        filterMenu.addSeparator();

        ButtonGroup group = new ButtonGroup();
        anyTypeItem = new JRadioButtonMenuItem("Tipo: cualquiera (console*)", true);
        consoleTypeItem = new JRadioButtonMenuItem("Tipo: console");
        consoleRestTypeItem = new JRadioButtonMenuItem("Tipo: consoleRest");
        consoleNotifTypeItem = new JRadioButtonMenuItem("Tipo: consoleNotif");

        group.add(anyTypeItem);
        group.add(consoleTypeItem);
        group.add(consoleRestTypeItem);
        group.add(consoleNotifTypeItem);

        anyTypeItem.addActionListener(e -> setConsoleType(ConsoleType.ANY));
        consoleTypeItem.addActionListener(e -> setConsoleType(ConsoleType.CONSOLE));
        consoleRestTypeItem.addActionListener(e -> setConsoleType(ConsoleType.CONSOLE_REST));
        consoleNotifTypeItem.addActionListener(e -> setConsoleType(ConsoleType.CONSOLE_NOTIF));

        filterMenu.add(anyTypeItem);
        filterMenu.add(consoleTypeItem);
        filterMenu.add(consoleRestTypeItem);
        filterMenu.add(consoleNotifTypeItem);

        menuBar.add(fileMenu);
        menuBar.add(filterMenu);

        return menuBar;
    }

    private JPanel buildContent() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        outputScrollPane = new JScrollPane(output);
        panel.add(outputScrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void installAutoScrollLoading() {
        if (outputScrollPane == null) {
            return;
        }

        // Mouse wheel gives us reliable intent even when scrollbar value doesn't change (e.g., at absolute end).
        outputScrollPane.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (ignoreScrollEvents || renderInProgress) {
                    return;
                }
                if (!onlyConsoleMenuItem.isSelected() || currentPath == null) {
                    return;
                }

                // e.getWheelRotation(): negative -> up, positive -> down
                int rot = e.getWheelRotation();
                if (rot < 0) {
                    // Up
                    if (paging.startOffset() == 0) {
                        return;
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastAutoLoadAtMs < 800) {
                        return;
                    }

                    var bar = outputScrollPane.getVerticalScrollBar();
                    if (bar.getValue() <= 40) {
                        lastAutoLoadAtMs = now;
                        pendingScrollDirection = ScrollDirection.UP;
                        paging.moveWindowUp();
                        analyze(currentPath);
                    }
                } else if (rot > 0) {
                    // Down
                    if (!hasMoreDown) {
                        return;
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastAutoLoadAtMs < 800) {
                        return;
                    }

                    var bar = outputScrollPane.getVerticalScrollBar();
                    int value = bar.getValue();
                    int extent = bar.getModel().getExtent();
                    int max = bar.getMaximum();
                    if (value + extent >= max - 40) {
                        lastAutoLoadAtMs = now;
                        pendingScrollDirection = ScrollDirection.DOWN;
                        paging.moveWindowDown();
                        analyze(currentPath);
                    }
                }
            }
        });

        outputScrollPane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
            private int lastValue = -1;

            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                }
                if (ignoreScrollEvents) {
                    return;
                }
                if (renderInProgress) {
                    return;
                }
                if (!onlyConsoleMenuItem.isSelected()) {
                    return;
                }
                if (currentPath == null) {
                    return;
                }

                var bar = outputScrollPane.getVerticalScrollBar();
                int value = bar.getValue();
                int extent = bar.getModel().getExtent();
                int max = bar.getMaximum();

                boolean userScrollingUp = (lastValue >= 0) && value < lastValue;
                boolean userScrollingDown = (lastValue >= 0) && value > lastValue;
                lastValue = value;

                long now = System.currentTimeMillis();
                if (now - lastAutoLoadAtMs < 800) {
                    return;
                }

                int topThresholdPx = 30;
                int bottomThresholdPx = 30;

                boolean nearTop = value <= topThresholdPx;
                boolean nearBottom = value + extent >= max - bottomThresholdPx;

                if (nearTop && (userScrollingUp || bar.getValue() == 0)) {
                    if (paging.startOffset() == 0) {
                        return;
                    }
                    lastAutoLoadAtMs = now;
                    pendingScrollDirection = ScrollDirection.UP;
                    paging.moveWindowUp();
                    analyze(currentPath);
                } else if (nearBottom && userScrollingDown) {
                    if (!hasMoreDown) {
                        return;
                    }
                    lastAutoLoadAtMs = now;
                    pendingScrollDirection = ScrollDirection.DOWN;
                    paging.moveWindowDown();
                    analyze(currentPath);
                }
            }
        });
    }

    private void reanalyzeIfPossible() {
        if (currentPath == null) {
            return;
        }
        // Debounce to avoid repeated heavy reads when user clicks several filters quickly
        debounceTimer.restart();
    }

    private void reanalyzeIfPossibleImmediate() {
        if (currentPath != null) {
            analyze(currentPath);
        }
    }

    private void analyze(Path path) {
        boolean onlyConsole = onlyConsoleMenuItem.isSelected();

        clearOutput();
        appendStyledLine("Analizando: " + path + (onlyConsole ? " (solo console)" : ""), LogLineStyler.styleForPlainText());

        new SwingWorker<LogAnalyzerService.AnalysisResult, Void>() {
            @Override
            protected LogAnalyzerService.AnalysisResult doInBackground() throws Exception {
                return service.analyze(path, new LogAnalyzerService.AnalysisOptions(DEFAULT_IGNORE_CONFIG, onlyConsole));
            }

            @Override
            protected void done() {
                try {
                    LogAnalyzerService.AnalysisResult result = get();
                    render(result);
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(LogAnalyzerFrame.this,
                            msg,
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    appendStyledLine("ERROR: " + msg, LogLineStyler.styleForLevel("ERROR"));
                } finally {
                    autoLoadInProgress = false;
                }
            }
        }.execute();
    }

    private void clearOutput() {
        output.setText("");
    }

    private void appendStyledLine(String line, javax.swing.text.AttributeSet style) {
        appendStyled(line, style);
        appendStyled(System.lineSeparator(), style);
    }

    private void appendStyledLogLine(String line) {
        var segs = LogLineStyler.styleLine(line);
        for (int i = 0; i < segs.size(); i++) {
            appendStyled(segs.text(i), segs.style(i));
        }
        appendStyled(System.lineSeparator(), LogLineStyler.styleForPlainText());
    }

    private void appendStyled(String text, javax.swing.text.AttributeSet style) {
        StyledDocument doc = output.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), text, style);
        } catch (BadLocationException e) {
            // Shouldn't happen; ignore
        }
    }

    private void render(LogAnalyzerService.AnalysisResult result) throws Exception {
        renderInProgress = true;
        ignoreScrollEvents = true;

        // Capture scroll position before we clear (to keep an anchor)
        int oldScrollValue = outputScrollPane != null ? outputScrollPane.getVerticalScrollBar().getValue() : 0;

        try {
            clearOutput();

            if (onlyConsoleMenuItem.isSelected()) {
                boolean showFullTrace = showFullTraceMenuItem.isSelected();

                appendStyledLine("Fichero analizado: " + result.analyzedFile(), LogLineStyler.styleForPlainText());
                appendStyledLine("Vista: líneas filtradas por " + selectedConsoleType.token() + (showFullTrace ? " (con traza)" : ""), LogLineStyler.styleForPlainText());
                appendStyledLine("Niveles: " + serializeLevels(selectedLevels), LogLineStyler.styleForPlainText());
                appendStyledLine("Ventana: offset=" + paging.startOffset() + " tamaño=" + paging.pageSize(), LogLineStyler.styleForPlainText());
                appendStyledLine("", LogLineStyler.styleForPlainText());

                Path fileToRead = service.resolveLogFile(result.analyzedFile()).orElse(result.analyzedFile());

                long pageStart = paging.startOffset();
                long pageEndExclusive = paging.startOffset() + paging.pageSize();
                long previousStartOffset = pageStart;

                renderWorker = new SwingWorker<>() {
                    private long matchesSeen = 0;
                    private int pageMatchesRendered = 0;
                    private boolean eofReached = false;

                    @Override
                    protected Void doInBackground() throws Exception {
                        try (BufferedReader reader = Files.newBufferedReader(fileToRead, StandardCharsets.UTF_8)) {
                            String line;
                            boolean endedByEof = true;
                            while (!isCancelled() && (line = reader.readLine()) != null) {
                                if (!consoleLineFilter.matches(line)) {
                                    continue;
                                }
                                if (!selectedLevels.isEmpty() && !logLevelFilter.matches(line)) {
                                    continue;
                                }

                                if (matchesSeen < pageStart) {
                                    matchesSeen++;
                                    if (showFullTrace) {
                                        skipUntilNextEvent(reader);
                                    }
                                    continue;
                                }

                                if (matchesSeen >= pageEndExclusive) {
                                    endedByEof = false;
                                    break;
                                }

                                publish(line);
                                pageMatchesRendered++;

                                if (showFullTrace) {
                                    publishTrace(reader);
                                }

                                publish("");
                                matchesSeen++;
                            }

                            eofReached = endedByEof;
                        }
                        return null;
                    }

                    private void skipUntilNextEvent(BufferedReader reader) throws Exception {
                        while (!isCancelled()) {
                            reader.mark(256 * 1024);
                            String next = reader.readLine();
                            if (next == null) {
                                return;
                            }
                            if (looksLikeNewLogEventLine(next)) {
                                reader.reset();
                                return;
                            }
                        }
                    }

                    private void publishTrace(BufferedReader reader) throws Exception {
                        while (!isCancelled()) {
                            reader.mark(256 * 1024);
                            String next = reader.readLine();
                            if (next == null) {
                                return;
                            }
                            if (looksLikeNewLogEventLine(next)) {
                                reader.reset();
                                return;
                            }
                            publish(next);
                        }
                    }

                    @Override
                    protected void process(List<String> chunks) {
                        for (String l : chunks) {
                            appendStyledLogLine(l);
                        }
                    }

                    @Override
                    protected void done() {
                        try {
                            get();
                        } catch (CancellationException ignored) {
                        } catch (Exception ex) {
                            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                            appendStyledLine("ERROR renderizando: " + msg, LogLineStyler.styleForLevel("ERROR"));
                        } finally {
                            if (pageMatchesRendered == 0 && eofReached) {
                                // We hit real end for this filter; no more down pages.
                                hasMoreDown = false;
                            }

                            if (pageMatchesRendered == 0 && eofReached && paging.startOffset() > 0) {
                                paging.moveWindowUp();
                                appendStyledLine("", LogLineStyler.styleForPlainText());
                                appendStyledLine("[FIN DEL LOG - no hay más coincidencias para los filtros actuales]", LogLineStyler.styleForPlainText());
                            }

                            renderInProgress = false;

                            // Restore a reasonable scroll anchor AFTER the document updates.
                            SwingUtilities.invokeLater(() -> {
                                try {
                                    if (outputScrollPane != null) {
                                        var bar = outputScrollPane.getVerticalScrollBar();
                                        if (pendingScrollDirection == ScrollDirection.UP) {
                                            // keep near-top to allow repeated up loads
                                            bar.setValue(10);
                                        } else if (pendingScrollDirection == ScrollDirection.DOWN) {
                                            // keep near-bottom
                                            bar.setValue(Math.max(0, bar.getMaximum() - bar.getModel().getExtent() - 10));
                                        } else {
                                            // default: keep previous position when re-rendering due to filters
                                            bar.setValue(Math.min(oldScrollValue, Math.max(0, bar.getMaximum() - bar.getModel().getExtent())));
                                        }
                                    }
                                } finally {
                                    pendingScrollDirection = ScrollDirection.NONE;
                                    ignoreScrollEvents = false;
                                }
                            });
                        }
                    }
                };

                renderWorker.execute();
                return;
            }

            appendStyledLine("Fichero analizado: " + result.analyzedFile(), LogLineStyler.styleForPlainText());
            appendStyledLine("Errores detectados (tras ignorados): " + result.summary().getTotalErrors(), LogLineStyler.styleForPlainText());
            appendStyledLine("", LogLineStyler.styleForPlainText());

            result.summary().getOccurrencesByType().entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue(java.util.Comparator.reverseOrder())
                            .thenComparing(Map.Entry.comparingByKey()))
                    .forEach(e -> appendStyledLine(e.getKey() + " -> " + e.getValue(), LogLineStyler.styleForPlainText()));
        } finally {
            if (!onlyConsoleMenuItem.isSelected()) {
                renderInProgress = false;
                SwingUtilities.invokeLater(() -> ignoreScrollEvents = false);
            }
        }
    }

    private void setConsoleType(ConsoleType type) {
        this.selectedConsoleType = type;
        this.consoleLineFilter = new ConsoleLineFilter(type);
        persistPreferences();
        paging.reset();
        reanalyzeIfPossible();
        pendingScrollDirection = ScrollDirection.NONE;
        hasMoreDown = true;
    }

    private void onLevelsChanged() {
        selectedLevels = newSelectedLevelsFromMenu();
        logLevelFilter = new LogLevelFilter(selectedLevels);
        persistPreferences();
        paging.reset();
        reanalyzeIfPossible();
        pendingScrollDirection = ScrollDirection.NONE;
        hasMoreDown = true;
    }

    private EnumSet<LogLevel> newSelectedLevelsFromMenu() {
        EnumSet<LogLevel> levels = EnumSet.noneOf(LogLevel.class);
        if (levelErrorItem != null && levelErrorItem.isSelected()) levels.add(LogLevel.ERROR);
        if (levelWarnItem != null && levelWarnItem.isSelected()) levels.add(LogLevel.WARN);
        if (levelInfoItem != null && levelInfoItem.isSelected()) levels.add(LogLevel.INFO);
        if (levelDebugItem != null && levelDebugItem.isSelected()) levels.add(LogLevel.DEBUG);
        if (levelTraceItem != null && levelTraceItem.isSelected()) levels.add(LogLevel.TRACE);
        return levels;
    }

    private static String serializeLevels(EnumSet<LogLevel> levels) {
        if (levels == null || levels.isEmpty()) {
            return "";
        }
        return levels.stream().map(Enum::name).sorted().reduce((a, b) -> a + "," + b).orElse("");
    }

    private static EnumSet<LogLevel> deserializeLevels(String s) {
        if (s == null || s.isBlank()) {
            return EnumSet.of(LogLevel.ERROR, LogLevel.WARN, LogLevel.INFO);
        }
        EnumSet<LogLevel> out = EnumSet.noneOf(LogLevel.class);
        for (String part : s.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            try {
                out.add(LogLevel.valueOf(p));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (out.isEmpty()) {
            return EnumSet.of(LogLevel.ERROR, LogLevel.WARN, LogLevel.INFO);
        }
        return out;
    }

    private static boolean looksLikeNewLogEventLine(String line) {
        if (line == null) {
            return false;
        }
        return LogLineStyler.looksLikeTimestampedLogLine(line);
    }

    private void persistPreferences() {
        // Persist filter state
        prefs.putBoolean(PREF_ONLY_CONSOLE, onlyConsoleMenuItem.isSelected());
        prefs.putBoolean(PREF_SHOW_TRACE, showFullTraceMenuItem.isSelected());
        prefs.put(PREF_CONSOLE_TYPE, selectedConsoleType.token());
        prefs.put(PREF_LEVELS, serializeLevels(selectedLevels));

        if (currentPath != null) {
            prefs.put(PREF_LAST_PATH, currentPath.toString());
            Path dir = currentPath;
            try {
                if (Files.isRegularFile(dir)) {
                    dir = dir.getParent();
                }
            } catch (Exception ignored) {
            }
            if (dir != null) {
                prefs.put(PREF_LAST_DIR, dir.toString());
            }
        }
    }

    private void restorePreferences() {
        String lastPathStr = prefs.get(PREF_LAST_PATH, null);

        onlyConsoleMenuItem.setSelected(prefs.getBoolean(PREF_ONLY_CONSOLE, false));
        showFullTraceMenuItem.setSelected(prefs.getBoolean(PREF_SHOW_TRACE, false));

        selectedConsoleType = ConsoleType.fromToken(prefs.get(PREF_CONSOLE_TYPE, "any"));
        selectedLevels = deserializeLevels(prefs.get(PREF_LEVELS, ""));

        consoleLineFilter = new ConsoleLineFilter(selectedConsoleType);
        logLevelFilter = new LogLevelFilter(selectedLevels);

        // Restore menu states if menus already built
        if (levelErrorItem != null) {
            levelErrorItem.setSelected(selectedLevels.contains(LogLevel.ERROR));
            levelWarnItem.setSelected(selectedLevels.contains(LogLevel.WARN));
            levelInfoItem.setSelected(selectedLevels.contains(LogLevel.INFO));
            levelDebugItem.setSelected(selectedLevels.contains(LogLevel.DEBUG));
            levelTraceItem.setSelected(selectedLevels.contains(LogLevel.TRACE));
        }

        if (anyTypeItem != null) {
            switch (selectedConsoleType) {
                case CONSOLE -> consoleTypeItem.setSelected(true);
                case CONSOLE_REST -> consoleRestTypeItem.setSelected(true);
                case CONSOLE_NOTIF -> consoleNotifTypeItem.setSelected(true);
                case ANY -> anyTypeItem.setSelected(true);
            }
        }

        if (lastPathStr != null && !lastPathStr.isBlank()) {
            try {
                currentPath = Path.of(lastPathStr);
                if (Files.exists(currentPath)) {
                    analyze(currentPath);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void chooseAndAnalyze(boolean directory) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(directory ? "Abrir directorio" : "Abrir archivo");
        chooser.setFileSelectionMode(directory ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
        if (!directory) {
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.addChoosableFileFilter(new FileNameExtensionFilter("Archivos de log (*.log, *.txt)", "log", "txt"));
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentPath = chooser.getSelectedFile().toPath();
            analyze(currentPath);
        }
    }
}