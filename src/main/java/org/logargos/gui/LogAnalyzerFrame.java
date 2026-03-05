package org.logargos.gui;

import org.logargos.app.LogAnalyzerService;
import org.logargos.filter.ConsoleLineFilter;
import org.logargos.filter.ConsoleLineFilter.ConsoleType;

import javax.swing.ButtonGroup;
import javax.swing.BorderFactory;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.JRadioButtonMenuItem;

/**
 * Simple Swing UI: open a log file/directory and optionally filter to console lines.
 */
public final class LogAnalyzerFrame extends JFrame {

    private static final Path DEFAULT_IGNORE_CONFIG = Path.of("config", "ignored-errors.txt");

    private final LogAnalyzerService service = new LogAnalyzerService();

    private ConsoleType selectedConsoleType = ConsoleType.ANY;

    // Keep a filter instance so it can be reused when rendering
    private ConsoleLineFilter consoleLineFilter = new ConsoleLineFilter(selectedConsoleType);

    private final JTextArea output = new JTextArea();
    private final JCheckBoxMenuItem onlyConsoleMenuItem = new JCheckBoxMenuItem("Solo líneas console ('- console -')");
    private final JCheckBoxMenuItem showFullTraceMenuItem = new JCheckBoxMenuItem("Mostrar traza completa (stacktrace)");

    private Path currentPath;

    private static final String PREF_LAST_PATH = "lastPath";
    private static final String PREF_LAST_DIR = "lastDir";
    private static final String PREF_ONLY_CONSOLE = "onlyConsole";
    private static final String PREF_SHOW_TRACE = "showTrace";
    private static final String PREF_CONSOLE_TYPE = "consoleType";

    private final Preferences prefs = Preferences.userNodeForPackage(LogAnalyzerFrame.class);

    private JRadioButtonMenuItem anyTypeItem;
    private JRadioButtonMenuItem consoleTypeItem;
    private JRadioButtonMenuItem consoleRestTypeItem;
    private JRadioButtonMenuItem consoleNotifTypeItem;

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

        // Restore last configuration
        restorePreferences();
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

        panel.add(new JScrollPane(output), BorderLayout.CENTER);
        return panel;
    }

    private void restorePreferences() {
        String lastPathStr = prefs.get(PREF_LAST_PATH, null);
        String lastDirStr = prefs.get(PREF_LAST_DIR, null);

        onlyConsoleMenuItem.setSelected(prefs.getBoolean(PREF_ONLY_CONSOLE, false));
        showFullTraceMenuItem.setSelected(prefs.getBoolean(PREF_SHOW_TRACE, false));

        ConsoleType type = ConsoleType.fromToken(prefs.get(PREF_CONSOLE_TYPE, "any"));
        this.selectedConsoleType = type;
        this.consoleLineFilter = new ConsoleLineFilter(type);

        // Update radio buttons to reflect restored type
        if (anyTypeItem != null) {
            switch (type) {
                case CONSOLE -> consoleTypeItem.setSelected(true);
                case CONSOLE_REST -> consoleRestTypeItem.setSelected(true);
                case CONSOLE_NOTIF -> consoleNotifTypeItem.setSelected(true);
                case ANY -> anyTypeItem.setSelected(true);
            }
        }

        // Normalize persisted value (store canonical token)
        prefs.put(PREF_CONSOLE_TYPE, type.token());

        if (lastPathStr != null && !lastPathStr.isBlank()) {
            try {
                Path p = Path.of(lastPathStr);
                this.currentPath = p;
                // Don't auto-analyze if path doesn't exist; UI will stay idle.
                if (Files.exists(p)) {
                    analyze(p);
                }
            } catch (Exception ignored) {
                // ignore invalid stored path
            }
        }

        // Seed file chooser directory if current path isn't available
        if (this.currentPath == null && lastDirStr != null && !lastDirStr.isBlank()) {
            try {
                Path d = Path.of(lastDirStr);
                if (Files.exists(d)) {
                    this.currentPath = d;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void persistPreferences() {
        // Persist filter state
        prefs.putBoolean(PREF_ONLY_CONSOLE, onlyConsoleMenuItem.isSelected());
        prefs.putBoolean(PREF_SHOW_TRACE, showFullTraceMenuItem.isSelected());
        prefs.put(PREF_CONSOLE_TYPE, selectedConsoleType.token());

        // Persist last path and directory for chooser
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

    private void chooseAndAnalyze(boolean directory) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(directory ? "Selecciona un directorio de logs" : "Selecciona un archivo de log");
        chooser.setFileSelectionMode(directory ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);

        String lastDirStr = prefs.get(PREF_LAST_DIR, null);
        if (lastDirStr != null && !lastDirStr.isBlank()) {
            try {
                Path lastDir = Path.of(lastDirStr);
                if (Files.exists(lastDir)) {
                    chooser.setCurrentDirectory(lastDir.toFile());
                }
            } catch (Exception ignored) {
            }
        }

        if (!directory) {
            chooser.setFileFilter(new FileNameExtensionFilter("Logs (*.log, *.txt)", "log", "txt"));
        }

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selected = chooser.getSelectedFile().toPath();
        analyze(selected);
    }

    private void reanalyzeIfPossible() {
        if (currentPath != null) {
            analyze(currentPath);
        }
    }

    private void analyze(Path path) {
        this.currentPath = path;
        persistPreferences();

        boolean onlyConsole = onlyConsoleMenuItem.isSelected();

        output.setText("Analizando: " + path + (onlyConsole ? " (solo console)" : "") + "\n");

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
                    output.append("\nERROR: " + msg + "\n");
                }
            }
        }.execute();
    }

    private void setConsoleType(ConsoleType type) {
        this.selectedConsoleType = type;
        this.consoleLineFilter = new ConsoleLineFilter(type);
        persistPreferences();
        reanalyzeIfPossible();
    }

    private static boolean looksLikeNewLogEventLine(String line) {
        if (line == null) {
            return false;
        }
        // Same timestamp style handled by LogParser: "2026-03-04T10:32:41.624 ..." or "2026-03-04 10:32:41,624 ..."
        return line.matches("^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:[,.]\\d{3})?.*");
    }

    private void render(LogAnalyzerService.AnalysisResult result) throws Exception {
        if (onlyConsoleMenuItem.isSelected()) {
            boolean showFullTrace = showFullTraceMenuItem.isSelected();

            StringBuilder sb = new StringBuilder();
            sb.append("Fichero analizado: ").append(result.analyzedFile()).append("\n");
            sb.append("Vista: líneas filtradas por ").append(selectedConsoleType.token()).append(showFullTrace ? " (con traza)" : "").append("\n\n");

            Path fileToRead = service.resolveLogFile(result.analyzedFile()).orElse(result.analyzedFile());

            try (BufferedReader reader = Files.newBufferedReader(fileToRead, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!consoleLineFilter.matches(line)) {
                        continue;
                    }

                    sb.append(line).append("\n");

                    if (!showFullTrace) {
                        continue;
                    }

                    // Full trace mode: append lines until the next *log event* starts (timestamp).
                    // This prevents leaking other channels (console/struts/HotRod) into the current event.
                    while (true) {
                        reader.mark(256 * 1024);
                        String next = reader.readLine();
                        if (next == null) {
                            break;
                        }

                        if (looksLikeNewLogEventLine(next)) {
                            reader.reset();
                            break;
                        }

                        sb.append(next).append("\n");
                    }

                    sb.append("\n");
                }
            }

            output.setText(sb.toString());
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Fichero analizado: ").append(result.analyzedFile()).append("\n");
        sb.append("Errores detectados (tras ignorados): ").append(result.summary().getTotalErrors()).append("\n\n");

        for (Map.Entry<String, Long> e : result.summary().getOccurrencesByType().entrySet()) {
            sb.append(e.getKey()).append(" -> ").append(e.getValue()).append("\n");
        }

        output.setText(sb.toString());
    }
}
