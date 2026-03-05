package org.logargos.gui;

import javax.swing.SwingUtilities;

/**
 * Swing entry point for the log analyzer GUI.
 */
public final class SwingLogAnalyzerApplication {

    private SwingLogAnalyzerApplication() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LogAnalyzerFrame frame = new LogAnalyzerFrame();
            frame.setVisible(true);
        });
    }
}
