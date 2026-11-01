// Java port of ga68's LINE_T (a68-types.h).
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/**
 * A source line in the internal copy of the source file (LINE_T).
 *
 * <p>The C version stores a GCC {@code location_t} from which FILENAME and
 * LINE_NO are derived through the line map. Here the file name and the line
 * number are stored directly.
 *
 * <p>The text is kept as a Java {@code String} in which every char holds one
 * byte of the source file (ISO-8859-1 decoding). This keeps the scanner
 * byte-exact with the C original, including UTF-8 sequences inside strings.
 */
public final class Line {
    /** Line text, normally including the trailing newline (STRING). */
    public String string;
    /** File the line belongs to (FILENAME); "prelude"/"postlude" for the environ. */
    public String filename;
    /** 1-based line number within {@link #filename} (LINE_NO). */
    public int number;
    public Line next;
    public Line previous;
    /** GCC location of column 0 of this line (LOCATION), in {@link #maps}. */
    public long location = LineMaps.UNKNOWN_LOCATION;
    /** The line table the location belongs to (C: the global line_table). */
    public LineMaps maps;

    public Line(String string, String filename, int number) {
        this.string = string;
        this.filename = filename;
        this.number = number;
    }

    /**
     * Character at index {@code i}, or {@code '\0'} past the end. This mirrors
     * reading a NUL-terminated C string.
     */
    public char at(int i) {
        return i < string.length() ? string.charAt(i) : '\0';
    }

    /** strlen of the C string starting at index {@code i}. */
    public int lengthFrom(int i) {
        int nul = string.indexOf('\0', i);
        int end = nul < 0 ? string.length() : nul;
        return Math.max(0, end - i);
    }

    @Override
    public String toString() {
        return filename + ":" + number;
    }
}
