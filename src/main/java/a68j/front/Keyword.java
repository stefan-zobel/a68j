// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/** A keyword, kept in a binary search tree (KEYWORD_T). */
public final class Keyword {
    public final Attribute attribute;
    public final String text;
    public Keyword less, more;

    public Keyword(Attribute attribute, String text) {
        this.attribute = attribute;
        this.text = text;
    }

    @Override
    public String toString() {
        return text + "=" + attribute;
    }
}
