// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/**
 * A mode (MOID_T).
 *
 * <p>{@link #attribute} is one of PROC_SYMBOL, ROWS_SYMBOL, REF_SYMBOL,
 * FLEX_SYMBOL, STRUCT_SYMBOL, UNION_SYMBOL, IN_TYPE_MODE, OUT_TYPE_MODE,
 * SERIES_MODE, INDICANT, MODULE_INDICANT or STANDARD. The meaning of
 * {@link #sub} and {@link #dim} depends on it; see the long comment on
 * MOID_T in a68-types.h.
 *
 * <p>GCC-specific members (ctype: a GENERIC tree, asm_label) are not ported.
 */
public final class Moid {
    public int number;
    public Attribute attribute = Attribute.STOP;
    public int dim;
    public boolean hasRows, hasRefs, use, portable, derivate;
    public Node node;
    public Pack pack;
    public Moid sub, equivalentMode, slice, deflexedMode, name, multipleMode, next, rowed, trim;

    /** DEFLEX (p). */
    public Moid deflex() {
        return deflexedMode != null ? deflexedMode : this;
    }
}
