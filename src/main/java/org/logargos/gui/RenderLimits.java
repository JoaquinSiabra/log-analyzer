package org.logargos.gui;

/**
 * Rendering limits to keep the Swing UI responsive on very large logs.
 */
public record RenderLimits(
        int maxLines,
        int maxCharacters
) {
    public static RenderLimits defaults() {
        return new RenderLimits(20_000, 5_000_000);
    }
}
