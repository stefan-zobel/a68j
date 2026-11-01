// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/**
 * Global state of the parser (PARSER_T).
 *
 * <p>The C version also holds two jmp_bufs used to bail out of the bottom-up
 * and top-down parsers; in Java these become exceptions.
 */
public final class ParserState {
    /** Tag that signifies an error. */
    public Tag errorTag;
    /** Control flag for the recursive tokeniser main loop. */
    public boolean stopScanner;
    /** Usable size of {@link #scanBuf}. */
    public int maxScanBufLength;
    /**
     * Scratch buffer used by the scanner. Kept as a char array (rather than a
     * StringBuilder) so that the C buffer arithmetic can be ported 1:1.
     */
    public char[] scanBuf;
    /** Counter used to number tags (a68_new_tag). */
    public int tagNumber;
}
