// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/** Static properties describing how a value was obtained (ORIGIN_T). */
public final class Origin {
    public Kindo kindo = Kindo.KINDO_NIL;
    /** Block number of the origin. */
    public int bno;
    /** Flag: dereferencing of the origin. */
    public boolean derefo;
    /** Flag: local generator of the origin. */
    public boolean geno;
    /** Location of the origin (C: location_t). */
    public Location diago;
}
