package org.logargos.gui;

/**
 * Keeps paging state for viewing large logs without loading everything in memory.
 * Offsets are expressed in number of matching header events (after filters), not raw file line numbers.
 */
public final class PaginationState {
    private int pageSize;

    /**
     * Start offset of the currently visible window (in matching events).
     */
    private long startOffset;

    /**
     * Cursor offset for forward streaming (how far we already loaded when appending at bottom).
     */
    private long forwardCursor;

    public PaginationState(int pageSize) {
        this.pageSize = Math.max(100, pageSize);
        this.startOffset = 0;
        this.forwardCursor = 0;
    }

    public int pageSize() {
        return pageSize;
    }

    public long startOffset() {
        return startOffset;
    }

    public long forwardCursor() {
        return forwardCursor;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = Math.max(100, pageSize);
    }

    public void reset() {
        this.startOffset = 0;
        this.forwardCursor = 0;
    }

    public void moveWindowUp() {
        this.startOffset = Math.max(0, startOffset - pageSize);
        // moving up doesn't change forwardCursor
    }

    public void moveWindowDown() {
        this.startOffset += pageSize;
        // keep forwardCursor at least at end of window
        this.forwardCursor = Math.max(forwardCursor, startOffset + pageSize);
    }

    public void ensureForwardCursorAtLeast(long cursor) {
        this.forwardCursor = Math.max(this.forwardCursor, cursor);
    }
}