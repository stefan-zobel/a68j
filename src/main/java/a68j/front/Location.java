// Source locations for the Java port of ga68.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/**
 * A source location: file, 1-based line and 1-based column. Replaces GCC's
 * {@code location_t}.
 */
public record Location(String file, int line, int column) {

    public static final Location UNKNOWN = new Location("<unknown>", 0, 0);

    /**
     * Location of index {@code pos} within line {@code l} (a68_get_line_location).
     * As in GCC, the column is 0 (unknown) if the line map cannot encode it.
     */
    public static Location of(Line l, int pos) {
        if (l == null) {
            return UNKNOWN;
        }
        if (l.maps == null) {
            return new Location(l.filename, l.number, Math.max(pos, 0) + 1);
        }
        long loc = l.maps.positionForLocAndOffset(l.location, Math.max(pos, 0) + 1L);
        LineMaps.Expanded e = l.maps.expand(loc);
        if (e == null) {
            return UNKNOWN;
        }
        return new Location(e.file(), (int) e.line(), (int) e.column());
    }

    /** Location of a token node. */
    public static Location of(Node p) {
        if (p == null || p.info == null || p.info.line == null) {
            return UNKNOWN;
        }
        return of(p.info.line, p.info.charInLine);
    }

    @Override
    public String toString() {
        return column == 0 ? file + ":" + line : file + ":" + line + ":" + column;
    }
}
