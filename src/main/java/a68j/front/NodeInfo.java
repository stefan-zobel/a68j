// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/**
 * Additional attributes of a parse tree node (NODE_INFO_T).
 *
 * <p>The C version keeps {@code char *} pointers into the line text
 * (char_in_line, comment_char_in_line, pragmat_char_in_line). Here these are
 * 0-based indexes into {@link Line#string} of the corresponding line.
 */
public final class NodeInfo {
    public int procedureLevel;
    /** Operator priority (PRIO), set by the bottom-up parser. */
    public int priority;
    /** Index of the first character of the token in {@link #line}; -1 if unset. */
    public int charInLine = -1;

    public Attribute commentType = Attribute.STOP;
    public String comment;
    public Line commentLine;
    public int commentCharInLine = -1;

    public Attribute pragmatType = Attribute.STOP;
    public String pragmat;
    public Line pragmatLine;
    public int pragmatCharInLine = -1;

    /** Symbol text of the token (NSYMBOL), set by the scanner. */
    public String symbol;
    /** Source line the token originates from. */
    public Line line;
}
