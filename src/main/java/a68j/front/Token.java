// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/** Interned token text, kept in a binary search tree ordered by strcmp (TOKEN_T). */
public final class Token {
    public final String text;
    public Token less, more;

    public Token(String text) {
        this.text = text;
    }
}
