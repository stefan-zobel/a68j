// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/** Stropping regimes (enum a68_stropping). */
public enum Stropping {
    /** Bold words in upper case, tags in lower case. */
    UPPER,
    /** GNU SUPPER: reserved words and tags in lower case, mode/operator indications capitalised. */
    SUPPER
}
