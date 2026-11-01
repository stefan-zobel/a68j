// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/** A pack of moids, e.g. procedure parameters or struct fields (PACK_T). */
public final class Pack {
    public Moid type;
    public String text;
    public Node node;
    public Pack next, previous;
}
