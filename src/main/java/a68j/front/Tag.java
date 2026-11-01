// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/**
 * An entry in a symbol table (TAG_T). See the long comment on TAG_T in
 * a68-types.h for the meaning of the fields.
 *
 * <p>GCC-specific members (tree_decl, ctype, lowerer) are not ported; of
 * the lowerer only whether it is LOWERER_UNIMPL is kept ({@link #unimplemented}).
 */
public final class Tag {
    public Table symbolTable;
    public Moid type;
    public Node node, unit;
    public String value;
    public boolean scopeAssigned, use, inProc, nestProc, locAssigned, portable, variable;
    public boolean ascribedRoutineText, isRecursive, publicized;
    public int priority;
    /** HEAP_SYMBOL or LOC_SYMBOL for variable declarations. */
    public Attribute heap = Attribute.STOP;
    public int scope, youngestEnviron, number;
    public int status;
    public Moif moif;
    public Origin origin;
    public Access access = Access.ACCESS_DIR;
    public Tag next, body;
    public String externSymbol;
    /**
     * A standard identifier or operator whose lowerer in ga68 is
     * LOWERER_UNIMPL (a68_lower_unimplemented): its use is a fatal error.
     */
    public boolean unimplemented;
}
