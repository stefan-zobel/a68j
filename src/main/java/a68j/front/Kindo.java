// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/** Kind of origin (enum a68_kindo). See a68-parser-sprops.cc. */
public enum Kindo {
    KINDO_NIL, KINDO_CST, KINDO_IDE, KINDO_VAR, KINDO_GEN
}
