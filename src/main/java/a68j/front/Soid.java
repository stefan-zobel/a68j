// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/** Sort/mode pair used by the mode checker (SOID_T). */
public final class Soid {
    public Attribute attribute = Attribute.STOP;
    /** Sort of context: STRONG, FIRM, MEEK, WEAK, SOFT or NO_SORT. */
    public Attribute sort = Attribute.STOP;
    public boolean cast;
    public Moid type;
    public Node node;
    public Soid next;
}
