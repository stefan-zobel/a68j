// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/** Module interface extract (EXTRACT_T). */
public final class Extract {
    /** One of the GA68_EXTRACT_* codes. */
    public int kind;
    /** Mangled symbol name. */
    public String symbol;
    public Moid mode;
    public int priority;
    public boolean variable, inProc;
}
