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

    public LogAnalyzerFrame() {
        super("LogArgos - Log Analyzer");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));

        setJMenuBar(buildMenuBar());
        setContentPane(buildContent());

        output.setEditable(false);
        output.setFont(output.getFont().deriveFont(12f));

        onlyConsoleMenuItem.addActionListener(e -> reanalyzeIfPossible());
        showFullTraceMenuItem.addActionListener(e -> reanalyzeIfPossible());
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
        JRadioButtonMenuItem any = new JRadioButtonMenuItem("Tipo: cualquiera (console*)", true);
        JRadioButtonMenuItem console = new JRadioButtonMenuItem("Tipo: console");
        JRadioButtonMenuItem consoleRest = new JRadioButtonMenuItem("Tipo: consoleRest");
        JRadioButtonMenuItem consoleNotif = new JRadioButtonMenuItem("Tipo: consoleNotif");

        group.add(any);
        group.add(console);
        group.add(consoleRest);
        group.add(consoleNotif);

        any.addActionListener(e -> setConsoleType(ConsoleType.ANY));
        console.addActionListener(e -> setConsoleType(ConsoleType.CONSOLE));
        consoleRest.addActionListener(e -> setConsoleType(ConsoleType.CONSOLE_REST));
        consoleNotif.addActionListener(e -> setConsoleType(ConsoleType.CONSOLE_NOTIF));

        filterMenu.add(any);
        filterMenu.add(console);
        filterMenu.add(consoleRest);
        filterMenu.add(consoleNotif);

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

    private void chooseAndAnalyze(boolean directory) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(directory ? "Selecciona un directorio de logs" : "Selecciona un archivo de log");
        chooser.setFileSelectionMode(directory ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);

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