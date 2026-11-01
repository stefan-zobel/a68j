// Emulation of the parts of GCC's libcpp/line-map.cc used by ga68.
// Original: Copyright (C) 2000-2025 Free Software Foundation, Inc.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import java.util.ArrayList;
import java.util.List;

/**
 * A port of the ordinary line maps of GCC's {@code line_table}
 * (libcpp/line-map.cc): {@code linemap_add}, {@code linemap_line_start} and
 * {@code linemap_position_for_loc_and_offset}, plus the expansion of a
 * location into file, line and column.
 *
 * <p>ga68 stores a {@code location_t} per source line and derives token
 * locations from it. Whether GCC can encode a column depends on how the maps
 * were laid out: columns of 128 and above, and every column on the last line
 * of the prelude or of an included file, cannot be represented and GCC then
 * reports the bare line ("file:line:"). Emulating the maps reproduces this.
 * Macro maps and ad-hoc locations are not needed by ga68 and are left out.
 */
public final class LineMaps {

    /** enum lc_reason (only the values ga68 uses). */
    public enum Reason { LC_ENTER, LC_LEAVE, LC_RENAME }

    public static final long UNKNOWN_LOCATION = 0;
    public static final long RESERVED_LOCATION_COUNT = 2;
    static final long LINE_MAP_MAX_COLUMN_NUMBER = (1L << 31) - 1;
    static final long LINE_MAP_MAX_LOCATION_WITH_PACKED_RANGES = 0x50000000L << 31;
    static final long LINE_MAP_MAX_LOCATION_WITH_COLS = 0x60000000L << 31;
    static final long LINE_MAP_MAX_LOCATION = 0x70000000L << 31;
    /** MAX_LOCATION_T + 1, the lowest macro location when there are no macro maps. */
    static final long MACRO_LOWEST_LOCATION = (-1L >>> 2) + 1;
    /** line_map_suggested_range_bits, set as default_range_bits in toplev.cc. */
    static final int DEFAULT_RANGE_BITS = 7;

    /** struct line_map_ordinary */
    static final class Map {
        long startLocation;
        Reason reason;
        boolean sysp;
        String toFile;
        long toLine;
        int columnAndRangeBits;
        int rangeBits;
        long includedFrom;
    }

    private final List<Map> maps = new ArrayList<>();
    private long highestLocation = RESERVED_LOCATION_COUNT - 1;
    private long highestLine = RESERVED_LOCATION_COUNT - 1;
    private long maxColumnHint;
    private int depth;

    /** An expanded location; column 0 means that no column is known. */
    public record Expanded(String file, long line, long column) {}

    private Map last() {
        return maps.get(maps.size() - 1);
    }

    private static long nextStartLocation(long startLoc, int rangeBits) {
        long mask = (1L << rangeBits) - 1;
        return (startLoc + mask) & ~mask;
    }

    private static long sourceLine(Map map, long loc) {
        return ((loc - map.startLocation) >>> map.columnAndRangeBits) + map.toLine;
    }

    private static long sourceColumn(Map map, long loc) {
        return ((loc - map.startLocation) & ((1L << map.columnAndRangeBits) - 1)) >>> map.rangeBits;
    }

    private static boolean mainFileP(Map map) {
        return map.includedFrom == 0;
    }

    /** linemap_ordinary_map_lookup: index of the map containing LOC, or -1. */
    private int lookup(long loc) {
        if (maps.isEmpty() || loc < maps.get(0).startLocation) {
            return -1;
        }
        int mn = 0;
        int mx = maps.size();
        while (mx - mn > 1) {
            int md = (mn + mx) / 2;
            if (maps.get(md).startLocation > loc) {
                mx = md;
            } else {
                mn = md;
            }
        }
        return mn;
    }

    /** linemap_included_from_linemap */
    private int includedFromLinemap(Map map) {
        return map.includedFrom == 0 ? -1 : lookup(map.includedFrom);
    }

    /** linemap_add; returns the new map or null when leaving the main file. */
    public Map add(Reason reason, boolean sysp, String toFile, long toLine) {
        long startLocation = highestLocation + 1;
        int rangeBits = 0;
        if (Long.compareUnsigned(startLocation, LINE_MAP_MAX_LOCATION_WITH_COLS) < 0) {
            rangeBits = DEFAULT_RANGE_BITS;
        }
        startLocation = nextStartLocation(startLocation, rangeBits);

        // If we are leaving the main file, return a NULL map.
        if (reason == Reason.LC_LEAVE && mainFileP(last()) && toFile == null) {
            depth--;
            return null;
        }
        if (Long.compareUnsigned(startLocation, LINE_MAP_MAX_LOCATION) >= 0) {
            // We ran out of line map space.
            startLocation = 0;
        }
        Map map = new Map();
        map.startLocation = startLocation;
        maps.add(map);
        int idx = maps.size() - 1;
        map.reason = reason;
        if (toFile != null && toFile.isEmpty()) {
            toFile = "<stdin>";
        }
        int from = -1;
        if (reason == Reason.LC_LEAVE) {
            from = includedFromLinemap(maps.get(idx - 1));
            if (toFile == null) {
                Map f = maps.get(from);
                toFile = f.toFile;
                if (maps.get(from + 1).reason == Reason.LC_RENAME) {
                    toLine = sourceLine(f, maps.get(idx - 1).includedFrom) + 1;
                } else {
                    toLine = sourceLine(f, maps.get(from + 1).startLocation);
                }
                sysp = f.sysp;
            }
        }
        map.sysp = sysp;
        map.toFile = toFile;
        map.toLine = toLine;
        map.rangeBits = map.columnAndRangeBits = 0;
        highestLocation = startLocation;
        highestLine = startLocation;
        maxColumnHint = 0;

        if (reason == Reason.LC_ENTER) {
            if (depth == 0) {
                map.includedFrom = 0;
            } else {
                int i = -1;
                while (maps.get(idx + i).startLocation == map.startLocation) {
                    --i;
                }
                Map mi = maps.get(idx + i);
                map.includedFrom = ((map.startLocation - 1 - mi.startLocation)
                        & ~((1L << mi.columnAndRangeBits) - 1)) + mi.startLocation;
            }
            depth++;
        } else if (reason == Reason.LC_RENAME) {
            map.includedFrom = maps.get(idx - 1).includedFrom;
        } else {
            depth--;
            map.includedFrom = maps.get(from).includedFrom;
        }
        return map;
    }

    /** linemap_line_start: location of column 0 of line TO_LINE in the current file. */
    public long lineStart(long toLine, long maxColumnHint) {
        Map map = last();
        long highest = highestLocation;
        long r;
        long lastLine = sourceLine(map, highestLine);
        long lineDelta = toLine - lastLine;
        boolean addMap = false;
        int effectiveColumnBits = map.columnAndRangeBits - map.rangeBits;

        if (lineDelta < 0
                || (lineDelta > 10 && lineDelta * map.columnAndRangeBits > 1000)
                || (maxColumnHint >= (1L << effectiveColumnBits))
                || (maxColumnHint <= 80 && effectiveColumnBits >= 10)
                || (Long.compareUnsigned(highest, LINE_MAP_MAX_LOCATION_WITH_PACKED_RANGES) > 0 && map.rangeBits > 0)
                || (Long.compareUnsigned(highest, LINE_MAP_MAX_LOCATION_WITH_COLS) > 0
                    && (this.maxColumnHint != 0 || Long.compareUnsigned(highest, LINE_MAP_MAX_LOCATION) >= 0))) {
            addMap = true;
        } else {
            maxColumnHint = this.maxColumnHint;
        }
        if (addMap) {
            int columnBits;
            int rangeBits;
            if (maxColumnHint > LINE_MAP_MAX_COLUMN_NUMBER
                    || Long.compareUnsigned(highest, LINE_MAP_MAX_LOCATION_WITH_COLS) > 0) {
                // Give up on column numbers (and on packed ranges).
                maxColumnHint = 1;
                columnBits = 0;
                rangeBits = 0;
                if (Long.compareUnsigned(highest, LINE_MAP_MAX_LOCATION) >= 0) {
                    return overflowed();
                }
            } else {
                columnBits = 7;
                if (Long.compareUnsigned(highest, LINE_MAP_MAX_LOCATION_WITH_PACKED_RANGES) <= 0) {
                    rangeBits = DEFAULT_RANGE_BITS;
                } else {
                    rangeBits = 0;
                }
                while (maxColumnHint >= (1L << columnBits)) {
                    columnBits++;
                }
                maxColumnHint = 1L << columnBits;
                columnBits += rangeBits;
            }
            // Allocate the new line_map. However, if the current map only has a
            // single line we can sometimes just increase its column_bits instead.
            if (lineDelta < 0
                    || lastLine != map.toLine
                    || sourceColumn(map, highest) >= (1L << (columnBits - rangeBits))
                    || ((toLine - map.toLine) & 0xFFFFFFFFL) >= (1L << (32 - columnBits))
                    || rangeBits < map.rangeBits) {
                map = add(Reason.LC_RENAME, map.sysp, map.toFile, toLine);
            }
            map.columnAndRangeBits = columnBits;
            map.rangeBits = rangeBits;
            r = map.startLocation + ((toLine - map.toLine) << columnBits);
        } else {
            r = highestLine + (lineDelta << map.columnAndRangeBits);
        }
        // Locations of ordinary tokens are always lower than locations of macro tokens.
        if (Long.compareUnsigned(r, LINE_MAP_MAX_LOCATION) >= 0) {
            return overflowed();
        }
        highestLine = r;
        if (Long.compareUnsigned(r, highestLocation) > 0) {
            highestLocation = r;
        }
        this.maxColumnHint = maxColumnHint;
        return r;
    }

    private long overflowed() {
        highestLine = highestLocation = LINE_MAP_MAX_LOCATION - 1;
        maxColumnHint = 1;
        return 0;
    }

    /** linemap_position_for_line_and_column */
    private long positionForLineAndColumn(Map map, long line, long column) {
        long r = map.startLocation;
        r += (line - map.toLine) << map.columnAndRangeBits;
        if (Long.compareUnsigned(r, LINE_MAP_MAX_LOCATION_WITH_COLS) <= 0) {
            r += (column & ((1L << map.columnAndRangeBits) - 1)) << map.rangeBits;
        }
        if (Long.compareUnsigned(r, MACRO_LOWEST_LOCATION) >= 0) {
            r = MACRO_LOWEST_LOCATION - 1;
        }
        if (Long.compareUnsigned(r, highestLocation) > 0) {
            highestLocation = r;
        }
        return r;
    }

    /** linemap_position_for_loc_and_offset */
    public long positionForLocAndOffset(long loc, long columnOffset) {
        if (columnOffset == 0 || Long.compareUnsigned(loc, RESERVED_LOCATION_COUNT) < 0) {
            return loc;
        }
        int mi = lookup(loc);
        if (mi < 0) {
            return loc;
        }
        Map map = maps.get(mi);
        long shiftedOffset = columnOffset << map.rangeBits;
        // The new location (loc + offset) should be higher than the first
        // location encoded by MAP.
        if (Long.compareUnsigned(map.startLocation, loc + shiftedOffset) >= 0) {
            return loc;
        }
        long line = sourceLine(map, loc);
        long column = sourceColumn(map, loc);
        // If MAP is not the last line map of its set, then the new location
        // (loc + offset) should be less than the first location encoded by the
        // next line map of the set.
        for (; mi != maps.size() - 1
                && Long.compareUnsigned(loc + shiftedOffset, maps.get(mi + 1).startLocation) >= 0; mi++) {
            Map next = maps.get(mi + 1);
            if (next.reason != Reason.LC_RENAME
                    || line < next.toLine
                    || !next.toFile.equals(maps.get(mi).toFile)) {
                return loc;
            }
        }
        map = maps.get(mi);
        column += columnOffset;
        // Bail out if the column is not representable within the existing linemap.
        if (column >= (1L << (map.columnAndRangeBits - map.rangeBits))) {
            return loc;
        }
        long r = positionForLineAndColumn(map, line, column);
        if (!(Long.compareUnsigned(r, highestLocation) <= 0) || lookup(r) != mi) {
            return loc;
        }
        return r;
    }

    /** in_system_header_at: whether LOC lies in a map entered as a system header (prelude, postlude). */
    public boolean inSystemHeader(long loc) {
        if (Long.compareUnsigned(loc, RESERVED_LOCATION_COUNT) < 0) {
            return false;
        }
        int mi = lookup(loc);
        return mi >= 0 && maps.get(mi).sysp;
    }

    /** expand_location for an ordinary location; null for reserved locations. */
    public Expanded expand(long loc) {
        if (Long.compareUnsigned(loc, RESERVED_LOCATION_COUNT) < 0) {
            return null;
        }
        int mi = lookup(loc);
        if (mi < 0) {
            return null;
        }
        Map map = maps.get(mi);
        return new Expanded(map.toFile, sourceLine(map, loc), sourceColumn(map, loc));
    }
}
