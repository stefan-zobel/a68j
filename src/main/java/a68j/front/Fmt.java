// Java port of ga68's a68-pretty-print.h: custom %e format tokens.
// Original: Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/**
 * Format tokens for the {@code %e} directive in diagnostics.
 *
 * <p>In C, {@code a68_error (p, "... %e ...", &tok)} receives the address of an
 * {@code a68_*_format_token}. In Java, pass the object returned by one of the
 * factory methods below; its {@link #toString} is inserted.
 */
public final class Fmt {
    private final String str;

    private Fmt(String str) {
        this.str = str;
    }

    @Override
    public String toString() {
        return str;
    }

    /** a68_attr_format_token */
    public static Fmt attr(Attribute a) {
        return new Fmt(Parser.attributeName(a));
    }

    /** a68_construct_format_token (attribute) */
    public static Fmt construct(Attribute a) {
        String nt = Parser.attributeName(a);
        return new Fmt(nt != null ? nt : "construct");
    }

    /** a68_construct_format_token (node) */
    public static Fmt construct(Node p) {
        return construct(p.attribute);
    }

    /**
     * a68_symbol_format_token: the symbol as written in the source if it can
     * be recovered from the line, otherwise the normalised symbol text.
     */
    public static Fmt symbol(Node p) {
        String txt = p.info == null ? null : p.info.symbol;
        if (txt == null) {
            return new Fmt("symbol");
        }
        Line line = p.info.line;
        int pos = p.info.charInLine;
        if (line == null || pos < 0) {
            return new Fmt(txt);
        }
        int size = txt.length();
        if (txt.isEmpty() || txt.charAt(0) != line.at(pos) || line.lengthFrom(pos) < size) {
            return new Fmt(txt);
        }
        StringBuilder m = new StringBuilder();
        int t = 0;
        int sym = pos;
        int n = 0;
        while (n < size) {
            char sc = line.at(sym);
            // Sic: in C, m_str[n] is written only if printable, but n advances
            // only when the character matches; the net effect is collecting the
            // matching printable characters.
            if (CType.toLower(txt.charAt(t)) == CType.toLower(sc)) {
                if (CType.isPrint(sc)) {
                    m.append(sc);
                } else {
                    m.append('\0');
                }
                t++;
                n++;
            }
            sym++;
        }
        return new Fmt(m.toString());
    }

    /** a68_sort_format_token */
    public static Fmt sort(Attribute s) {
        return new Fmt(switch (s) {
            case NO_SORT -> "this";
            case SOFT -> "a soft";
            case WEAK -> "a weak";
            case MEEK -> "a meek";
            case FIRM -> "a firm";
            case STRONG -> "a strong";
            default -> throw new IllegalStateException("unreachable");
        });
    }

    /** a68_line_format_token */
    public static Fmt line(Line l, Node n) {
        if (l.filename.equals("prelude") || l.filename.equals("postlude")) {
            return new Fmt("in standard environment");
        } else if (n != null && n.info != null && n.info.line != null && l.number == n.info.line.number) {
            return new Fmt("in this line");
        } else {
            return new Fmt("in line " + l.number);
        }
    }

    /** a68_moid_format_token */
    public static Fmt moid(A68 a68, Moid m) {
        return new Fmt(MoidsToString.moidToString(a68, m, Parser.MOID_ERROR_WIDTH, null));
    }

    /** a68_opmoid_format_token */
    public static Fmt opmoid(A68 a68, Moid m) {
        if (m == null || m == a68.modes.ERROR) {
            m = a68.modes.UNDEFINED;
        }
        String str;
        if (m == a68.modes.VOID) {
            str = a68.options().stropping == Stropping.UPPER ? "UNION (VOID, ..)" : "union (void, ..)";
        } else if (m.attribute == Attribute.SERIES_MODE) {
            if (m.pack != null && m.pack.next == null) {
                str = MoidsToString.moidToString(a68, m.pack.type, Parser.MOID_ERROR_WIDTH, null);
            } else {
                str = MoidsToString.moidToString(a68, m, Parser.MOID_ERROR_WIDTH, null);
            }
        } else {
            str = MoidsToString.moidToString(a68, m, Parser.MOID_ERROR_WIDTH, null);
        }
        return new Fmt(str);
    }
}
