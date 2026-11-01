// Java port of ga68's a68-parser-prelude.cc: standard prelude definitions.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * Standard prelude definitions (a68-parser-prelude.cc): enters the standard
 * modes, identifiers, priorities and operators in the standard environ.
 *
 * <h2>Porting notes</h2>
 * <ul>
 * <li>The lowerer arguments ({@code a68_lower_*}) of {@code a68_idf} and
 * {@code a68_op} and the {@code LOWERER_T} parameter of
 * {@code add_a68_standenv} are GCC-specific and omitted. Every entry is kept,
 * in the original order, since the order in which tags are inserted into the
 * standard environ matters.</li>
 * <li>The variadic {@code a68_proc} takes Java varargs; the terminating
 * {@code NO_MOID} is omitted.</li>
 * <li>{@code &TOP_MOID (&A68_JOB)} is passed as
 * {@code Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v)}, a mode
 * slot {@code &M_X} as {@code Ref.of(() -> a68.modes.X, v -> a68.modes.X = v)}.</li>
 * </ul>
 */
public final class Prelude {
    private Prelude() {}

    private static final boolean A68_STD = true;
    private static final boolean A68_EXT = false;

    /** A68C_DEFIO: ALGOL68C type procs. */
    // Sic: the macro is not used anywhere in the original.
    private static void a68cDefio(A68 a68, Ref<Moid> m, String name, Moid mode) {
        m.set(proc(a68, mode, a68.modes.REF_FILE));
        idf(a68, A68_EXT, "get" + name, m.get());
        m.set(proc(a68, a68.modes.VOID, a68.modes.REF_FILE, mode));
        idf(a68, A68_EXT, "put" + name, m.get());
        m.set(proc(a68, mode));
        idf(a68, A68_EXT, "read" + name, m.get());
        m.set(proc(a68, a68.modes.VOID, mode));
        idf(a68, A68_EXT, "print" + name, m.get());
    }

    /** INSERT_TAG (local to add_a68_standenv). */
    private static void insertTag(A68 a68, Ref<Tag> l, Tag n) {
        n.next = l.get();
        l.set(n);
    }

    /** add_a68_standenv: enter tag in standenv symbol table. */
    private static Tag addA68Standenv(A68 a68, boolean portable, Attribute a, Node n, String c, Moid m,
            int p) {
        // GCC-specific, omitted: LOWERER_T parameter l (default LOWERER_UNIMPL).
        Tag newOne = Parser.newTag(a68);

        n.info.procedureLevel = 0;
        newOne.use = false;
        newOne.heap = HEAP_SYMBOL;
        newOne.symbolTable = a68.standenv;
        newOne.node = n;
        if (c != null) {
            newOne.value = Keywords.addToken(a68, c).text;
        } else {
            newOne.value = null;
        }
        newOne.priority = p;
        // GCC-specific, omitted: TAX_TREE_DECL (new_one) = NULL; LOWERER (new_one) = l
        // (only whether l is LOWERER_UNIMPL is kept, see idfUnimpl and opUnimpl).
        newOne.unit = null;
        newOne.portable = portable;
        newOne.type = m;
        newOne.next = null;
        if (a == IDENTIFIER) {
            insertTag(a68, Ref.of(() -> a68.standenv.identifiers, v -> a68.standenv.identifiers = v), newOne);
        } else if (a == OP_SYMBOL) {
            insertTag(a68, Ref.of(() -> a68.standenv.operators, v -> a68.standenv.operators = v), newOne);
        } else if (a == PRIO_SYMBOL) {
            insertTag(a68, Ref.of(() -> a68.standenv.priority, v -> a68.standenv.priority = v), newOne);
        } else if (a == INDICANT) {
            insertTag(a68, Ref.of(() -> a68.standenv.indicants, v -> a68.standenv.indicants = v), newOne);
        } else if (a == LABEL) {
            insertTag(a68, Ref.of(() -> a68.standenv.labels, v -> a68.standenv.labels = v), newOne);
        }
        return newOne;
    }

    /** a68_proc: compose PROC moid from arguments - first result, than arguments. */
    private static Moid proc(A68 a68, Moid m, Moid... attribute) {
        Pack p = null, q = null;

        for (Moid y : attribute) {
            // C: the va_arg loop stops at the first NO_MOID.
            if (y == null) {
                break;
            }
            Pack newOne = Parser.newPack(a68);

            newOne.type = y;
            newOne.text = null;
            newOne.next = null;
            if (q != null) {
                q.next = newOne;
            } else {
                p = newOne;
            }
            q = newOne;
        }

        return ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), PROC_SYMBOL,
                ModeBuilder.countPackMembers(a68, p), null, m, p);
    }

    /** a68_idf: enter an identifier in standenv. */
    private static void idf(A68 a68, boolean portable, String n, Moid m) {
        // GCC-specific, omitted: LOWERER_T parameter l (default LOWERER_UNIMPL).
        addA68Standenv(a68, portable, IDENTIFIER,
                Parser.someNode(a68, Keywords.addToken(a68, n).text),
                null, m, 0);
    }

    /**
     * a68_idf without a lowerer (LOWERER_UNIMPL): ga68 declares the
     * identifier but cannot compile a use of it (a68_lower_unimplemented).
     */
    private static void idfUnimpl(A68 a68, boolean portable, String n, Moid m) {
        addA68Standenv(a68, portable, IDENTIFIER,
                Parser.someNode(a68, Keywords.addToken(a68, n).text),
                null, m, 0).unimplemented = true;
    }

    /** a68_mode: enter a moid in standenv. */
    private static void mode(A68 a68, int p, String t, Ref<Moid> m) {
        m.set(ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v),
                STANDARD, p,
                Parser.someNode(a68, Keywords.findKeyword(a68, t).text),
                null, null));
    }

    /** a68_prio: enter a priority in standenv. */
    private static void prio(A68 a68, String p, int b) {
        addA68Standenv(a68, true, PRIO_SYMBOL,
                Parser.someNode(a68, Keywords.addToken(a68, p).text),
                null, null, b);
    }

    /** a68_op: enter operator in standenv. */
    private static void op(A68 a68, boolean portable, String n, Moid m) {
        // GCC-specific, omitted: LOWERER_T parameter l (default LOWERER_UNIMPL).
        addA68Standenv(a68, portable, OP_SYMBOL,
                Parser.someNode(a68, Keywords.addToken(a68, n).text),
                null, m, 0);
    }

    /** a68_op without a lowerer (LOWERER_UNIMPL), see {@link #idfUnimpl}. */
    private static void opUnimpl(A68 a68, boolean portable, String n, Moid m) {
        addA68Standenv(a68, portable, OP_SYMBOL,
                Parser.someNode(a68, Keywords.addToken(a68, n).text),
                null, m, 0).unimplemented = true;
    }

    /** stand_moids: enter standard modes in standenv. */
    private static void standMoids(A68 a68) {
        // Primitive A68 moids.
        mode(a68, 0, "VOID", Ref.of(() -> a68.modes.VOID, v -> a68.modes.VOID = v));
        // Standard precision.
        mode(a68, 0, "INT", Ref.of(() -> a68.modes.INT, v -> a68.modes.INT = v));
        mode(a68, 0, "REAL", Ref.of(() -> a68.modes.REAL, v -> a68.modes.REAL = v));
        mode(a68, 0, "COMPL", Ref.of(() -> a68.modes.COMPLEX, v -> a68.modes.COMPLEX = v));
        mode(a68, 0, "BITS", Ref.of(() -> a68.modes.BITS, v -> a68.modes.BITS = v));
        mode(a68, 0, "BYTES", Ref.of(() -> a68.modes.BYTES, v -> a68.modes.BYTES = v));
        // Multiple precision.
        mode(a68, -2, "INT", Ref.of(() -> a68.modes.SHORT_SHORT_INT, v -> a68.modes.SHORT_SHORT_INT = v));
        mode(a68, -2, "BITS", Ref.of(() -> a68.modes.SHORT_SHORT_BITS, v -> a68.modes.SHORT_SHORT_BITS = v));
        mode(a68, -1, "INT", Ref.of(() -> a68.modes.SHORT_INT, v -> a68.modes.SHORT_INT = v));
        mode(a68, -1, "BITS", Ref.of(() -> a68.modes.SHORT_BITS, v -> a68.modes.SHORT_BITS = v));
        mode(a68, 1, "INT", Ref.of(() -> a68.modes.LONG_INT, v -> a68.modes.LONG_INT = v));
        mode(a68, 1, "REAL", Ref.of(() -> a68.modes.LONG_REAL, v -> a68.modes.LONG_REAL = v));
        mode(a68, 1, "COMPL", Ref.of(() -> a68.modes.LONG_COMPLEX, v -> a68.modes.LONG_COMPLEX = v));
        mode(a68, 1, "BITS", Ref.of(() -> a68.modes.LONG_BITS, v -> a68.modes.LONG_BITS = v));
        mode(a68, 1, "BYTES", Ref.of(() -> a68.modes.LONG_BYTES, v -> a68.modes.LONG_BYTES = v));
        mode(a68, 2, "REAL", Ref.of(() -> a68.modes.LONG_LONG_REAL, v -> a68.modes.LONG_LONG_REAL = v));
        mode(a68, 2, "INT", Ref.of(() -> a68.modes.LONG_LONG_INT, v -> a68.modes.LONG_LONG_INT = v));
        mode(a68, 2, "BITS", Ref.of(() -> a68.modes.LONG_LONG_BITS, v -> a68.modes.LONG_LONG_BITS = v));
        mode(a68, 2, "COMPL", Ref.of(() -> a68.modes.LONG_LONG_COMPLEX, v -> a68.modes.LONG_LONG_COMPLEX = v));
        // Other.
        mode(a68, 0, "BOOL", Ref.of(() -> a68.modes.BOOL, v -> a68.modes.BOOL = v));
        mode(a68, 0, "CHAR", Ref.of(() -> a68.modes.CHAR, v -> a68.modes.CHAR = v));
        mode(a68, 0, "STRING", Ref.of(() -> a68.modes.STRING, v -> a68.modes.STRING = v));
        mode(a68, 0, "FILE", Ref.of(() -> a68.modes.FILE, v -> a68.modes.FILE = v));
        mode(a68, 0, "CHANNEL", Ref.of(() -> a68.modes.CHANNEL, v -> a68.modes.CHANNEL = v));
        mode(a68, 0, "SEMA", Ref.of(() -> a68.modes.SEMA, v -> a68.modes.SEMA = v));
        // Rows.
        a68.modes.ROWS = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), ROWS_SYMBOL, 0, null, null, null);
        // REFs.
        a68.modes.REF_INT = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.INT, null);
        a68.modes.REF_REAL = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.REAL, null);
        a68.modes.REF_COMPLEX = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.COMPLEX, null);
        a68.modes.REF_BITS = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.BITS, null);
        a68.modes.REF_BYTES = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.BYTES, null);
        // Multiple precision.
        a68.modes.REF_LONG_INT = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.LONG_INT, null);
        a68.modes.REF_LONG_REAL = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.LONG_REAL, null);
        a68.modes.REF_LONG_COMPLEX = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.LONG_COMPLEX, null);
        a68.modes.REF_LONG_LONG_INT = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.LONG_LONG_INT, null);
        a68.modes.REF_LONG_LONG_REAL = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.LONG_LONG_REAL, null);
        a68.modes.REF_LONG_LONG_COMPLEX = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.LONG_LONG_COMPLEX, null);
        a68.modes.REF_LONG_BITS = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.LONG_BITS, null);
        a68.modes.REF_LONG_LONG_BITS = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.LONG_LONG_BITS, null);
        a68.modes.REF_LONG_BYTES = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.LONG_BYTES, null);
        a68.modes.REF_SHORT_INT = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.SHORT_INT, null);
        a68.modes.REF_SHORT_SHORT_INT = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.SHORT_SHORT_INT, null);
        a68.modes.REF_SHORT_BITS = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.SHORT_BITS, null);
        a68.modes.REF_SHORT_SHORT_BITS = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.SHORT_SHORT_BITS, null);
        // Other.
        a68.modes.REF_BOOL = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.BOOL, null);
        a68.modes.REF_CHAR = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.CHAR, null);
        a68.modes.REF_FILE = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.FILE, null);
        a68.modes.REF_REF_FILE = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.REF_FILE, null);
        // [] INT.
        a68.modes.ROW_INT = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), ROW_SYMBOL, 1, null, a68.modes.INT, null);
        a68.modes.ROW_INT.hasRows = true;
        a68.modes.ROW_INT.slice = a68.modes.INT;
        a68.modes.REF_ROW_INT = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.ROW_INT, null);
        a68.modes.REF_ROW_INT.name = a68.modes.REF_INT;
        // [] REAL.
        a68.modes.ROW_REAL = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), ROW_SYMBOL, 1, null, a68.modes.REAL, null);
        a68.modes.ROW_REAL.hasRows = true;
        a68.modes.ROW_REAL.slice = a68.modes.REAL;
        a68.modes.REF_ROW_REAL = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.ROW_REAL, null);
        a68.modes.REF_ROW_REAL.name = a68.modes.REF_REAL;
        // [,] REAL.
        a68.modes.ROW_ROW_REAL = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), ROW_SYMBOL, 2, null, a68.modes.REAL, null);
        a68.modes.ROW_ROW_REAL.hasRows = true;
        a68.modes.ROW_ROW_REAL.slice = a68.modes.ROW_REAL;
        a68.modes.REF_ROW_ROW_REAL = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.ROW_ROW_REAL, null);
        a68.modes.REF_ROW_ROW_REAL.name = a68.modes.REF_ROW_REAL;
        // [] COMPLEX.
        a68.modes.ROW_COMPLEX = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), ROW_SYMBOL, 1, null, a68.modes.COMPLEX, null);
        a68.modes.ROW_COMPLEX.hasRows = true;
        a68.modes.ROW_COMPLEX.slice = a68.modes.COMPLEX;
        a68.modes.REF_ROW_COMPLEX = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.ROW_COMPLEX, null);
        a68.modes.REF_ROW_COMPLEX.name = a68.modes.REF_COMPLEX;
        // [,] COMPLEX.
        a68.modes.ROW_ROW_COMPLEX = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), ROW_SYMBOL, 2, null, a68.modes.COMPLEX, null);
        a68.modes.ROW_ROW_COMPLEX.hasRows = true;
        a68.modes.ROW_ROW_COMPLEX.slice = a68.modes.ROW_COMPLEX;
        a68.modes.REF_ROW_ROW_COMPLEX = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.ROW_ROW_COMPLEX, null);
        a68.modes.REF_ROW_ROW_COMPLEX.name = a68.modes.REF_ROW_COMPLEX;
        // [] BOOL.
        a68.modes.ROW_BOOL = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), ROW_SYMBOL, 1, null, a68.modes.BOOL, null);
        a68.modes.ROW_BOOL.hasRows = true;
        a68.modes.ROW_BOOL.slice = a68.modes.BOOL;
        // FLEX [] BOOL.
        Moid m = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), FLEX_SYMBOL, 0, null, a68.modes.ROW_BOOL, null);
        m.hasRows = true;
        a68.modes.FLEX_ROW_BOOL = m;
        // [] BITS.
        a68.modes.ROW_BITS = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), ROW_SYMBOL, 1, null, a68.modes.BITS, null);
        a68.modes.ROW_BITS.hasRows = true;
        a68.modes.ROW_BITS.slice = a68.modes.BITS;
        // [] CHAR.
        a68.modes.ROW_CHAR = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), ROW_SYMBOL, 1, null, a68.modes.CHAR, null);
        a68.modes.ROW_CHAR.hasRows = true;
        a68.modes.ROW_CHAR.slice = a68.modes.CHAR;
        // [][] CHAR.
        a68.modes.ROW_ROW_CHAR = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), ROW_SYMBOL, 1, null, a68.modes.ROW_CHAR, null);
        a68.modes.ROW_ROW_CHAR.hasRows = true;
        a68.modes.ROW_ROW_CHAR.slice = a68.modes.ROW_CHAR;
        // MODE STRING = FLEX [] CHAR.
        m = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), FLEX_SYMBOL, 0, null, a68.modes.ROW_CHAR, null);
        m.hasRows = true;
        a68.modes.FLEX_ROW_CHAR = m;
        a68.modes.STRING.equivalentMode = m;
        // REF [] CHAR.
        a68.modes.REF_ROW_CHAR = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.ROW_CHAR, null);
        a68.modes.REF_ROW_CHAR.name = a68.modes.REF_CHAR;
        // PROC [] CHAR.
        a68.modes.PROC_ROW_CHAR = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), PROC_SYMBOL, 0, null, a68.modes.ROW_CHAR, null);
        // REF STRING = REF FLEX [] CHAR.
        a68.modes.REF_STRING = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.STRING.equivalentMode, null);
        a68.modes.REF_STRING.name = a68.modes.REF_CHAR;
        a68.modes.REF_STRING.deflexedMode = a68.modes.REF_ROW_CHAR;
        // [] STRING.
        a68.modes.ROW_STRING = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), ROW_SYMBOL, 1, null, a68.modes.STRING, null);
        a68.modes.ROW_STRING.hasRows = true;
        a68.modes.ROW_STRING.slice = a68.modes.STRING;
        a68.modes.ROW_STRING.deflexedMode = a68.modes.ROW_ROW_CHAR;
        // PROC STRING.
        a68.modes.PROC_STRING = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), PROC_SYMBOL, 0, null, a68.modes.STRING, null);
        a68.modes.PROC_STRING.deflexedMode = a68.modes.PROC_ROW_CHAR;
        // COMPLEX.
        Ref<Pack> z = Ref.box(null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.REAL, Keywords.addToken(a68, "im").text, null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.REAL, Keywords.addToken(a68, "re").text, null);
        m = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), STRUCT_SYMBOL, ModeBuilder.countPackMembers(a68, z.get()), null, null, z.get());
        a68.modes.COMPLEX.equivalentMode = m;
        z.set(null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.REF_REAL, Keywords.addToken(a68, "im").text, null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.REF_REAL, Keywords.addToken(a68, "re").text, null);
        m = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), STRUCT_SYMBOL, ModeBuilder.countPackMembers(a68, z.get()), null, null, z.get());
        a68.modes.REF_COMPLEX.name = m;
        // LONG COMPLEX.
        z.set(null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.LONG_REAL, Keywords.addToken(a68, "im").text, null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.LONG_REAL, Keywords.addToken(a68, "re").text, null);
        m = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), STRUCT_SYMBOL, ModeBuilder.countPackMembers(a68, z.get()), null, null, z.get());
        a68.modes.LONG_COMPLEX.equivalentMode = m;
        z.set(null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.REF_LONG_REAL, Keywords.addToken(a68, "im").text, null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.REF_LONG_REAL, Keywords.addToken(a68, "re").text, null);
        m = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), STRUCT_SYMBOL, ModeBuilder.countPackMembers(a68, z.get()), null, null, z.get());
        a68.modes.REF_LONG_COMPLEX.name = m;
        // LONG_LONG COMPLEX.
        z.set(null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.LONG_LONG_REAL, Keywords.addToken(a68, "im").text, null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.LONG_LONG_REAL, Keywords.addToken(a68, "re").text, null);
        m = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), STRUCT_SYMBOL, ModeBuilder.countPackMembers(a68, z.get()), null, null, z.get());
        a68.modes.LONG_LONG_COMPLEX.equivalentMode = m;
        z.set(null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.REF_LONG_LONG_REAL, Keywords.addToken(a68, "im").text, null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.REF_LONG_LONG_REAL, Keywords.addToken(a68, "re").text, null);
        m = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), STRUCT_SYMBOL, ModeBuilder.countPackMembers(a68, z.get()), null, null, z.get());
        a68.modes.REF_LONG_LONG_COMPLEX.name = m;
        // SEMA.
        z.set(null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.REF_INT, null, null);
        a68.modes.SEMA.equivalentMode = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), STRUCT_SYMBOL, ModeBuilder.countPackMembers(a68, z.get()), null, null, z.get());
        // PROC VOID.
        z.set(null);
        a68.modes.PROC_VOID = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), PROC_SYMBOL, ModeBuilder.countPackMembers(a68, z.get()), null, a68.modes.VOID, z.get());
        // PROC (REAL) REAL.
        z.set(null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.REAL, null, null);
        a68.modes.PROC_REAL_REAL = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), PROC_SYMBOL, ModeBuilder.countPackMembers(a68, z.get()), null, a68.modes.REAL, z.get());
        // PROC (LONG_REAL) LONG_REAL.
        z.set(null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.LONG_REAL, null, null);
        a68.modes.PROC_LONG_REAL_LONG_REAL = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), PROC_SYMBOL, ModeBuilder.countPackMembers(a68, z.get()), null, a68.modes.LONG_REAL, z.get());
        // IO: PROC (REF FILE) BOOL.
        z.set(null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.REF_FILE, null, null);
        a68.modes.PROC_REF_FILE_BOOL = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), PROC_SYMBOL, ModeBuilder.countPackMembers(a68, z.get()), null, a68.modes.BOOL, z.get());
        // IO: PROC (REF FILE) VOID.
        z.set(null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.REF_FILE, null, null);
        a68.modes.PROC_REF_FILE_VOID = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), PROC_SYMBOL, ModeBuilder.countPackMembers(a68, z.get()), null, a68.modes.VOID, z.get());
        // IO: SIMPLIN and SIMPLOUT.
        a68.modes.SIMPLIN = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), IN_TYPE_MODE, 0, null, null, null);
        a68.modes.ROW_SIMPLIN = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), ROW_SYMBOL, 1, null, a68.modes.SIMPLIN, null);
        a68.modes.ROW_SIMPLIN.slice = a68.modes.SIMPLIN;
        a68.modes.SIMPLOUT = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), OUT_TYPE_MODE, 0, null, null, null);
        a68.modes.ROW_SIMPLOUT = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), ROW_SYMBOL, 1, null, a68.modes.SIMPLOUT, null);
        a68.modes.ROW_SIMPLOUT.slice = a68.modes.SIMPLOUT;
    }

    /** stand_prelude: set up standenv - general RR including transput. */
    private static void standPrelude(A68 a68) {
        // GCC-specific, omitted: the lowerer arguments (a68_lower_*) of a68_idf and a68_op.
        // Identifiers.
        idf(a68, A68_STD, "intlengths", a68.modes.INT);
        idf(a68, A68_STD, "intshorths", a68.modes.INT);
        idf(a68, A68_STD, "maxint", a68.modes.INT);
        idf(a68, A68_STD, "longmaxint", a68.modes.LONG_INT);
        idf(a68, A68_STD, "longlongmaxint", a68.modes.LONG_LONG_INT);
        idf(a68, A68_STD, "shortmaxint", a68.modes.SHORT_INT);
        idf(a68, A68_STD, "shortshortmaxint", a68.modes.SHORT_SHORT_INT);
        idf(a68, A68_STD, "maxreal", a68.modes.REAL);
        idf(a68, A68_STD, "longmaxreal", a68.modes.LONG_REAL);
        idf(a68, A68_STD, "longlongmaxreal", a68.modes.LONG_LONG_REAL);
        idf(a68, A68_STD, "smallreal", a68.modes.REAL);
        idf(a68, A68_STD, "longsmallreal", a68.modes.LONG_REAL);
        idf(a68, A68_STD, "longlongsmallreal", a68.modes.LONG_LONG_REAL);
        idf(a68, A68_STD, "reallengths", a68.modes.INT);
        idf(a68, A68_STD, "realshorths", a68.modes.INT);
        idf(a68, A68_STD, "bitslengths", a68.modes.INT);
        idf(a68, A68_STD, "bitsshorths", a68.modes.INT);
        idf(a68, A68_STD, "bitswidth", a68.modes.INT);
        idf(a68, A68_STD, "longbitswidth", a68.modes.INT);
        idf(a68, A68_STD, "longlongbitswidth", a68.modes.INT);
        idf(a68, A68_STD, "shortbitswidth", a68.modes.INT);
        idf(a68, A68_STD, "shortshortbitswidth", a68.modes.INT);
        idf(a68, A68_STD, "maxbits", a68.modes.BITS);
        idf(a68, A68_STD, "longmaxbits", a68.modes.LONG_BITS);
        idf(a68, A68_STD, "longlongmaxbits", a68.modes.LONG_LONG_BITS);
        idf(a68, A68_STD, "shortmaxbits", a68.modes.SHORT_BITS);
        idf(a68, A68_STD, "shortshortmaxbits", a68.modes.SHORT_SHORT_BITS);
        idf(a68, A68_STD, "maxabschar", a68.modes.INT);
        idf(a68, A68_STD, "pi", a68.modes.REAL);
        idf(a68, A68_STD, "longpi", a68.modes.LONG_REAL);
        idf(a68, A68_STD, "longlongpi", a68.modes.LONG_LONG_REAL);
        idfUnimpl(a68, A68_STD, "compllengths", a68.modes.INT);
        idfUnimpl(a68, A68_STD, "complshorths", a68.modes.INT);
        idfUnimpl(a68, A68_STD, "byteslengths", a68.modes.INT);
        idfUnimpl(a68, A68_STD, "bytesshorths", a68.modes.INT);
        idfUnimpl(a68, A68_STD, "byteswidth", a68.modes.INT);
        idfUnimpl(a68, A68_STD, "longbyteswidth", a68.modes.INT);
        idf(a68, A68_STD, "flip", a68.modes.CHAR);
        idf(a68, A68_STD, "flop", a68.modes.CHAR);
        idf(a68, A68_STD, "errorchar", a68.modes.CHAR);
        idf(a68, A68_STD, "nullcharacter", a68.modes.CHAR);
        idf(a68, A68_STD, "blank", a68.modes.CHAR);
        // RNG procedures.
        Moid m = proc(a68, a68.modes.VOID, a68.modes.INT);
        idfUnimpl(a68, A68_STD, "firstrandom", m);
        // REAL procedures.
        m = a68.modeCache.procReal;
        idfUnimpl(a68, A68_STD, "nextrandom", m);
        idf(a68, A68_STD, "random", m);
        idfUnimpl(a68, A68_STD, "rnd", m);
        m = a68.modeCache.procRealReal;
        idf(a68, A68_STD, "arccos", m);
        idf(a68, A68_STD, "arcsin", m);
        idf(a68, A68_STD, "arctan", m);
        idf(a68, A68_STD, "cos", m);
        idf(a68, A68_STD, "exp", m);
        idf(a68, A68_STD, "ln", m);
        idf(a68, A68_STD, "sin", m);
        idf(a68, A68_STD, "sqrt", m);
        idf(a68, A68_STD, "tan", m);
        // LONG REAL procedures.
        m = proc(a68, a68.modes.LONG_REAL);
        idfUnimpl(a68, A68_STD, "longnextrandom", m);
        idf(a68, A68_STD, "longrandom", m);
        m = proc(a68, a68.modes.LONG_REAL, a68.modes.LONG_REAL);
        idf(a68, A68_STD, "longarccos", m);
        idf(a68, A68_STD, "longarcsin", m);
        idf(a68, A68_STD, "longarctan", m);
        idf(a68, A68_STD, "longcos", m);
        idf(a68, A68_STD, "longexp", m);
        idf(a68, A68_STD, "longln", m);
        idf(a68, A68_STD, "longsin", m);
        idf(a68, A68_STD, "longsqrt", m);
        idf(a68, A68_STD, "longtan", m);
        // LONG LONG REAL procedures.
        m = proc(a68, a68.modes.LONG_LONG_REAL);
        idfUnimpl(a68, A68_STD, "longlongnextrandom", m);
        idf(a68, A68_STD, "longlongrandom", m);
        m = proc(a68, a68.modes.LONG_LONG_REAL, a68.modes.LONG_LONG_REAL);
        idf(a68, A68_STD, "longlongarccos", m);
        idf(a68, A68_STD, "longlongarcsin", m);
        idf(a68, A68_STD, "longlongarctan", m);
        idf(a68, A68_STD, "longlongcos", m);
        idf(a68, A68_STD, "longlongexp", m);
        idf(a68, A68_STD, "longlongln", m);
        idf(a68, A68_STD, "longlongsin", m);
        idf(a68, A68_STD, "longlongsqrt", m);
        idf(a68, A68_STD, "longlongtan", m);
        // Priorities.
        prio(a68, "+:=", 1);
        prio(a68, "-:=", 1);
        prio(a68, "*:=", 1);
        prio(a68, "/:=", 1);
        prio(a68, "%:=", 1);
        prio(a68, "%*:=", 1);
        prio(a68, "+=:", 1);
        prio(a68, "PLUSAB", 1);
        prio(a68, "MINUSAB", 1);
        prio(a68, "TIMESAB", 1);
        prio(a68, "DIVAB", 1);
        prio(a68, "OVERAB", 1);
        prio(a68, "MODAB", 1);
        prio(a68, "PLUSTO", 1);
        prio(a68, "OR", 2);
        prio(a68, "AND", 3);
        prio(a68, "XOR", 3);
        prio(a68, "=", 4);
        prio(a68, "/=", 4);
        prio(a68, "<", 5);
        prio(a68, "<=", 5);
        prio(a68, ">", 5);
        prio(a68, ">=", 5);
        prio(a68, "EQ", 4);
        prio(a68, "NE", 4);
        prio(a68, "LT", 5);
        prio(a68, "LE", 5);
        prio(a68, "GT", 5);
        prio(a68, "GE", 5);
        prio(a68, "+", 6);
        prio(a68, "-", 6);
        prio(a68, "*", 7);
        prio(a68, "/", 7);
        prio(a68, "OVER", 7);
        prio(a68, "%", 7);
        prio(a68, "MOD", 7);
        prio(a68, "%*", 7);
        prio(a68, "ELEM", 7);
        prio(a68, "**", 8);
        prio(a68, "SHL", 8);
        prio(a68, "SHR", 8);
        prio(a68, "UP", 8);
        prio(a68, "DOWN", 8);
        prio(a68, "^", 8);
        prio(a68, "LWB", 8);
        prio(a68, "UPB", 8);
        prio(a68, "I", 9);
        prio(a68, "+*", 9);
        // BOOL operators.
        m = proc(a68, a68.modes.BOOL, a68.modes.BOOL);
        op(a68, A68_STD, "NOT", m);
        op(a68, A68_STD, "~", m);
        m = proc(a68, a68.modes.INT, a68.modes.BOOL);
        op(a68, A68_STD, "ABS", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.BOOL, a68.modes.BOOL);
        op(a68, A68_STD, "OR", m);
        op(a68, A68_STD, "AND", m);
        op(a68, A68_STD, "=", m);
        op(a68, A68_STD, "/=", m);
        op(a68, A68_STD, "EQ", m);
        op(a68, A68_STD, "NE", m);
        // CHAR operators.
        m = proc(a68, a68.modes.BOOL, a68.modes.CHAR, a68.modes.CHAR);
        op(a68, A68_STD, "=", m);
        op(a68, A68_STD, "/=", m);
        op(a68, A68_STD, "<", m);
        op(a68, A68_STD, "<=", m);
        op(a68, A68_STD, ">", m);
        op(a68, A68_STD, ">=", m);
        op(a68, A68_STD, "EQ", m);
        op(a68, A68_STD, "NE", m);
        op(a68, A68_STD, "LT", m);
        op(a68, A68_STD, "LE", m);
        op(a68, A68_STD, "GT", m);
        op(a68, A68_STD, "GE", m);
        m = proc(a68, a68.modes.INT, a68.modes.CHAR);
        op(a68, A68_STD, "ABS", m);
        m = proc(a68, a68.modes.CHAR, a68.modes.INT);
        op(a68, A68_STD, "REPR", m);
        // STRING operators.
        m = proc(a68, a68.modes.BOOL, a68.modes.STRING, a68.modes.STRING);
        op(a68, A68_STD, "=", m);
        op(a68, A68_STD, "/=", m);
        op(a68, A68_STD, "<", m);
        op(a68, A68_STD, "<=", m);
        op(a68, A68_STD, ">=", m);
        op(a68, A68_STD, ">", m);
        op(a68, A68_STD, "EQ", m);
        op(a68, A68_STD, "NE", m);
        op(a68, A68_STD, "LT", m);
        op(a68, A68_STD, "LE", m);
        op(a68, A68_STD, "GE", m);
        op(a68, A68_STD, "GT", m);
        m = proc(a68, a68.modes.STRING, a68.modes.CHAR, a68.modes.CHAR);
        op(a68, A68_STD, "+", m);
        m = proc(a68, a68.modes.STRING, a68.modes.STRING, a68.modes.STRING);
        op(a68, A68_STD, "+", m);
        m = proc(a68, a68.modes.REF_STRING, a68.modes.REF_STRING, a68.modes.STRING);
        op(a68, A68_STD, "+:=", m);
        op(a68, A68_STD, "PLUSAB", m);
        m = proc(a68, a68.modes.REF_STRING, a68.modes.REF_STRING, a68.modes.INT);
        op(a68, A68_STD, "*:=", m);
        op(a68, A68_STD, "TIMESAB", m);
        m = proc(a68, a68.modes.REF_STRING, a68.modes.STRING, a68.modes.REF_STRING);
        op(a68, A68_STD, "+=:", m);
        op(a68, A68_STD, "PLUSTO", m);
        m = proc(a68, a68.modes.STRING, a68.modes.STRING, a68.modes.INT);
        op(a68, A68_STD, "*", m);
        m = proc(a68, a68.modes.STRING, a68.modes.INT, a68.modes.STRING);
        op(a68, A68_STD, "*", m);
        m = proc(a68, a68.modes.STRING, a68.modes.INT, a68.modes.CHAR);
        op(a68, A68_STD, "*", m);
        m = proc(a68, a68.modes.STRING, a68.modes.CHAR, a68.modes.INT);
        op(a68, A68_STD, "*", m);
        // SHORT SHORT INT operators.
        m = proc(a68, a68.modes.SHORT_SHORT_INT, a68.modes.SHORT_SHORT_INT);
        op(a68, A68_STD, "+", m);
        op(a68, A68_STD, "-", m);
        op(a68, A68_STD, "ABS", m);
        m = proc(a68, a68.modes.INT, a68.modes.SHORT_SHORT_INT);
        op(a68, A68_STD, "SIGN", m);
        m = proc(a68, a68.modes.SHORT_INT, a68.modes.SHORT_SHORT_INT);
        op(a68, A68_STD, "LENG", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.SHORT_SHORT_INT);
        op(a68, A68_STD, "ODD", m);
        m = proc(a68, a68.modes.SHORT_SHORT_INT, a68.modes.SHORT_SHORT_INT, a68.modes.SHORT_SHORT_INT);
        op(a68, A68_STD, "+", m);
        op(a68, A68_STD, "-", m);
        op(a68, A68_STD, "*", m);
        op(a68, A68_STD, "OVER", m);
        op(a68, A68_STD, "%", m);
        op(a68, A68_STD, "MOD", m);
        op(a68, A68_STD, "%*", m);
        m = proc(a68, a68.modes.REF_SHORT_SHORT_INT, a68.modes.REF_SHORT_SHORT_INT, a68.modes.SHORT_SHORT_INT);
        op(a68, A68_STD, "+:=", m);
        op(a68, A68_STD, "-:=", m);
        op(a68, A68_STD, "*:=", m);
        op(a68, A68_STD, "%:=", m);
        op(a68, A68_STD, "%*:=", m);
        op(a68, A68_STD, "PLUSAB", m);
        op(a68, A68_STD, "MINUSAB", m);
        op(a68, A68_STD, "TIMESAB", m);
        op(a68, A68_STD, "OVERAB", m);
        op(a68, A68_STD, "MODAB", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.SHORT_SHORT_INT, a68.modes.SHORT_SHORT_INT);
        op(a68, A68_STD, "EQ", m);
        op(a68, A68_STD, "NE", m);
        op(a68, A68_STD, "GE", m);
        op(a68, A68_STD, "GT", m);
        op(a68, A68_STD, "LE", m);
        op(a68, A68_STD, "LT", m);
        op(a68, A68_STD, "=", m);
        op(a68, A68_STD, ">=", m);
        op(a68, A68_STD, ">", m);
        op(a68, A68_STD, "<=", m);
        op(a68, A68_STD, "<", m);
        op(a68, A68_STD, "/=", m);
        m = proc(a68, a68.modes.SHORT_SHORT_INT, a68.modes.SHORT_SHORT_INT, a68.modes.INT);
        op(a68, A68_STD, "**", m);
        op(a68, A68_STD, "^", m);
        // SHORT INT operators.
        m = proc(a68, a68.modes.SHORT_INT, a68.modes.SHORT_INT);
        op(a68, A68_STD, "+", m);
        op(a68, A68_STD, "-", m);
        op(a68, A68_STD, "ABS", m);
        m = proc(a68, a68.modes.SHORT_SHORT_INT, a68.modes.SHORT_INT);
        op(a68, A68_STD, "SHORTEN", m);
        m = proc(a68, a68.modes.INT, a68.modes.SHORT_INT);
        op(a68, A68_STD, "LENG", m);
        op(a68, A68_STD, "SIGN", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.SHORT_INT);
        op(a68, A68_STD, "ODD", m);
        m = proc(a68, a68.modes.SHORT_INT, a68.modes.SHORT_INT, a68.modes.SHORT_INT);
        op(a68, A68_STD, "+", m);
        op(a68, A68_STD, "-", m);
        op(a68, A68_STD, "*", m);
        op(a68, A68_STD, "OVER", m);
        op(a68, A68_STD, "%", m);
        op(a68, A68_STD, "MOD", m);
        op(a68, A68_STD, "%*", m);
        m = proc(a68, a68.modes.REF_SHORT_INT, a68.modes.REF_SHORT_INT, a68.modes.SHORT_INT);
        op(a68, A68_STD, "+:=", m);
        op(a68, A68_STD, "-:=", m);
        op(a68, A68_STD, "*:=", m);
        op(a68, A68_STD, "%:=", m);
        op(a68, A68_STD, "%*:=", m);
        op(a68, A68_STD, "PLUSAB", m);
        op(a68, A68_STD, "MINUSAB", m);
        op(a68, A68_STD, "TIMESAB", m);
        op(a68, A68_STD, "OVERAB", m);
        op(a68, A68_STD, "MODAB", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.SHORT_INT, a68.modes.SHORT_INT);
        op(a68, A68_STD, "=", m);
        op(a68, A68_STD, "EQ", m);
        op(a68, A68_STD, "/=", m);
        op(a68, A68_STD, "NE", m);
        op(a68, A68_STD, "<", m);
        op(a68, A68_STD, "LT", m);
        op(a68, A68_STD, "<=", m);
        op(a68, A68_STD, "LE", m);
        op(a68, A68_STD, ">", m);
        op(a68, A68_STD, "GT", m);
        op(a68, A68_STD, ">=", m);
        op(a68, A68_STD, "GE", m);
        m = proc(a68, a68.modes.SHORT_INT, a68.modes.SHORT_INT, a68.modes.INT);
        op(a68, A68_STD, "**", m);
        op(a68, A68_STD, "^", m);
        // INT operators.
        m = proc(a68, a68.modes.INT, a68.modes.INT);
        op(a68, A68_STD, "+", m);
        op(a68, A68_STD, "-", m);
        op(a68, A68_STD, "ABS", m);
        op(a68, A68_STD, "SIGN", m);
        m = proc(a68, a68.modes.SHORT_INT, a68.modes.INT);
        op(a68, A68_STD, "SHORTEN", m);
        m = proc(a68, a68.modes.LONG_INT, a68.modes.INT);
        op(a68, A68_STD, "LENG", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.INT);
        op(a68, A68_STD, "ODD", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.INT, a68.modes.INT);
        op(a68, A68_STD, "=", m);
        op(a68, A68_STD, "/=", m);
        op(a68, A68_STD, "<", m);
        op(a68, A68_STD, "<=", m);
        op(a68, A68_STD, ">", m);
        op(a68, A68_STD, ">=", m);
        op(a68, A68_STD, "EQ", m);
        op(a68, A68_STD, "NE", m);
        op(a68, A68_STD, "LT", m);
        op(a68, A68_STD, "LE", m);
        op(a68, A68_STD, "GT", m);
        op(a68, A68_STD, "GE", m);
        m = proc(a68, a68.modes.INT, a68.modes.INT, a68.modes.INT);
        op(a68, A68_STD, "+", m);
        op(a68, A68_STD, "-", m);
        op(a68, A68_STD, "*", m);
        op(a68, A68_STD, "OVER", m);
        op(a68, A68_STD, "%", m);
        op(a68, A68_STD, "MOD", m);
        op(a68, A68_STD, "%*", m);
        op(a68, A68_STD, "**", m);
        op(a68, A68_STD, "^", m);
        m = proc(a68, a68.modes.REAL, a68.modes.INT, a68.modes.INT);
        op(a68, A68_STD, "/", m);
        m = proc(a68, a68.modes.REF_INT, a68.modes.REF_INT, a68.modes.INT);
        op(a68, A68_STD, "+:=", m);
        op(a68, A68_STD, "-:=", m);
        op(a68, A68_STD, "*:=", m);
        op(a68, A68_STD, "%:=", m);
        op(a68, A68_STD, "%*:=", m);
        op(a68, A68_STD, "PLUSAB", m);
        op(a68, A68_STD, "MINUSAB", m);
        op(a68, A68_STD, "TIMESAB", m);
        op(a68, A68_STD, "OVERAB", m);
        op(a68, A68_STD, "MODAB", m);
        // LONG INT operators
        m = proc(a68, a68.modes.LONG_INT, a68.modes.LONG_INT);
        op(a68, A68_STD, "+", m);
        op(a68, A68_STD, "-", m);
        op(a68, A68_STD, "ABS", m);
        m = proc(a68, a68.modes.INT, a68.modes.LONG_INT);
        op(a68, A68_STD, "SHORTEN", m);
        op(a68, A68_STD, "SIGN", m);
        m = proc(a68, a68.modes.LONG_LONG_INT, a68.modes.LONG_INT);
        op(a68, A68_STD, "LENG", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.LONG_INT);
        op(a68, A68_STD, "ODD", m);
        m = proc(a68, a68.modes.LONG_INT, a68.modes.LONG_INT, a68.modes.LONG_INT);
        op(a68, A68_STD, "+", m);
        op(a68, A68_STD, "-", m);
        op(a68, A68_STD, "*", m);
        op(a68, A68_STD, "OVER", m);
        op(a68, A68_STD, "%", m);
        op(a68, A68_STD, "MOD", m);
        op(a68, A68_STD, "%*", m);
        m = proc(a68, a68.modes.REF_LONG_INT, a68.modes.REF_LONG_INT, a68.modes.LONG_INT);
        op(a68, A68_STD, "+:=", m);
        op(a68, A68_STD, "-:=", m);
        op(a68, A68_STD, "*:=", m);
        op(a68, A68_STD, "%:=", m);
        op(a68, A68_STD, "%*:=", m);
        op(a68, A68_STD, "PLUSAB", m);
        op(a68, A68_STD, "MINUSAB", m);
        op(a68, A68_STD, "TIMESAB", m);
        op(a68, A68_STD, "OVERAB", m);
        op(a68, A68_STD, "MODAB", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.LONG_INT, a68.modes.LONG_INT);
        op(a68, A68_STD, "=", m);
        op(a68, A68_STD, "EQ", m);
        op(a68, A68_STD, "/=", m);
        op(a68, A68_STD, "NE", m);
        op(a68, A68_STD, "<", m);
        op(a68, A68_STD, "LT", m);
        op(a68, A68_STD, "<=", m);
        op(a68, A68_STD, "LE", m);
        op(a68, A68_STD, ">", m);
        op(a68, A68_STD, "GT", m);
        op(a68, A68_STD, ">=", m);
        op(a68, A68_STD, "GE", m);
        m = proc(a68, a68.modes.LONG_REAL, a68.modes.LONG_INT, a68.modes.LONG_INT);
        op(a68, A68_STD, "/", m);
        m = proc(a68, a68.modes.LONG_INT, a68.modes.LONG_INT, a68.modes.INT);
        op(a68, A68_STD, "**", m);
        op(a68, A68_STD, "^", m);
        // LONG LONG INT operators.
        m = proc(a68, a68.modes.LONG_LONG_INT, a68.modes.LONG_LONG_INT);
        op(a68, A68_STD, "+", m);
        op(a68, A68_STD, "-", m);
        op(a68, A68_STD, "ABS", m);
        m = proc(a68, a68.modes.INT, a68.modes.LONG_LONG_INT);
        op(a68, A68_STD, "SIGN", m);
        m = proc(a68, a68.modes.LONG_INT, a68.modes.LONG_LONG_INT);
        op(a68, A68_STD, "SHORTEN", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.LONG_LONG_INT);
        op(a68, A68_STD, "ODD", m);
        m = proc(a68, a68.modes.LONG_LONG_INT, a68.modes.LONG_LONG_INT, a68.modes.LONG_LONG_INT);
        op(a68, A68_STD, "+", m);
        op(a68, A68_STD, "-", m);
        op(a68, A68_STD, "*", m);
        op(a68, A68_STD, "OVER", m);
        op(a68, A68_STD, "%", m);
        op(a68, A68_STD, "MOD", m);
        op(a68, A68_STD, "%*", m);
        m = proc(a68, a68.modes.REF_LONG_LONG_INT, a68.modes.REF_LONG_LONG_INT, a68.modes.LONG_LONG_INT);
        op(a68, A68_STD, "+:=", m);
        op(a68, A68_STD, "-:=", m);
        op(a68, A68_STD, "*:=", m);
        op(a68, A68_STD, "%:=", m);
        op(a68, A68_STD, "%*:=", m);
        op(a68, A68_STD, "PLUSAB", m);
        op(a68, A68_STD, "MINUSAB", m);
        op(a68, A68_STD, "TIMESAB", m);
        op(a68, A68_STD, "OVERAB", m);
        op(a68, A68_STD, "MODAB", m);
        m = proc(a68, a68.modes.LONG_LONG_REAL, a68.modes.LONG_LONG_INT, a68.modes.LONG_LONG_INT);
        op(a68, A68_STD, "/", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.LONG_LONG_INT, a68.modes.LONG_LONG_INT);
        op(a68, A68_STD, "EQ", m);
        op(a68, A68_STD, "NE", m);
        op(a68, A68_STD, "GE", m);
        op(a68, A68_STD, "GT", m);
        op(a68, A68_STD, "LE", m);
        op(a68, A68_STD, "LT", m);
        op(a68, A68_STD, "=", m);
        op(a68, A68_STD, ">=", m);
        op(a68, A68_STD, ">", m);
        op(a68, A68_STD, "<=", m);
        op(a68, A68_STD, "<", m);
        op(a68, A68_STD, "/=", m);
        m = proc(a68, a68.modes.LONG_LONG_INT, a68.modes.LONG_LONG_INT, a68.modes.INT);
        op(a68, A68_STD, "**", m);
        op(a68, A68_STD, "^", m);
        // SHORT SHORT BITS operators
        m = proc(a68, a68.modes.BOOL, a68.modes.SHORT_SHORT_BITS, a68.modes.SHORT_SHORT_BITS);
        op(a68, A68_STD, "=", m);
        op(a68, A68_STD, "EQ", m);
        op(a68, A68_STD, "/=", m);
        op(a68, A68_STD, "NE", m);
        op(a68, A68_STD, "<=", m);
        op(a68, A68_STD, "LE", m);
        op(a68, A68_STD, ">=", m);
        op(a68, A68_STD, "GE", m);
        m = proc(a68, a68.modes.SHORT_SHORT_BITS, a68.modes.SHORT_SHORT_BITS);
        op(a68, A68_STD, "NOT", m);
        op(a68, A68_STD, "~", m);
        m = proc(a68, a68.modes.SHORT_SHORT_BITS, a68.modes.SHORT_SHORT_BITS, a68.modes.SHORT_SHORT_BITS);
        op(a68, A68_STD, "AND", m);
        op(a68, A68_STD, "OR", m);
        m = proc(a68, a68.modes.SHORT_SHORT_BITS, a68.modes.SHORT_SHORT_BITS, a68.modes.INT);
        op(a68, A68_STD, "SHL", m);
        op(a68, A68_STD, "UP", m);
        op(a68, A68_STD, "SHR", m);
        op(a68, A68_STD, "DOWN", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.INT, a68.modes.SHORT_SHORT_BITS);
        op(a68, A68_STD, "ELEM", m);
        m = proc(a68, a68.modes.SHORT_SHORT_BITS, a68.modes.SHORT_SHORT_INT);
        op(a68, A68_STD, "BIN", m);
        m = proc(a68, a68.modes.SHORT_SHORT_INT, a68.modes.SHORT_SHORT_BITS);
        op(a68, A68_STD, "ABS", m);
        m = proc(a68, a68.modes.SHORT_BITS, a68.modes.SHORT_SHORT_BITS);
        op(a68, A68_STD, "LENG", m);
        // SHORT BITS operators.
        m = proc(a68, a68.modes.SHORT_INT, a68.modes.SHORT_BITS);
        op(a68, A68_STD, "ABS", m);
        m = proc(a68, a68.modes.SHORT_BITS, a68.modes.SHORT_INT);
        op(a68, A68_STD, "BIN", m);
        m = proc(a68, a68.modes.SHORT_BITS, a68.modes.SHORT_BITS);
        op(a68, A68_STD, "NOT", m);
        op(a68, A68_STD, "~", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.SHORT_BITS, a68.modes.SHORT_BITS);
        op(a68, A68_STD, "=", m);
        op(a68, A68_STD, "EQ", m);
        op(a68, A68_STD, "/=", m);
        op(a68, A68_STD, "NE", m);
        op(a68, A68_STD, "<=", m);
        op(a68, A68_STD, "LE", m);
        op(a68, A68_STD, ">=", m);
        op(a68, A68_STD, "GE", m);
        m = proc(a68, a68.modes.SHORT_BITS, a68.modes.SHORT_BITS, a68.modes.SHORT_BITS);
        op(a68, A68_STD, "AND", m);
        op(a68, A68_STD, "OR", m);
        m = proc(a68, a68.modes.SHORT_BITS, a68.modes.SHORT_BITS, a68.modes.INT);
        op(a68, A68_STD, "SHL", m);
        op(a68, A68_STD, "UP", m);
        op(a68, A68_STD, "SHR", m);
        op(a68, A68_STD, "DOWN", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.INT, a68.modes.SHORT_BITS);
        op(a68, A68_STD, "ELEM", m);
        m = proc(a68, a68.modes.SHORT_SHORT_BITS, a68.modes.SHORT_BITS);
        op(a68, A68_STD, "SHORTEN", m);
        m = proc(a68, a68.modes.BITS, a68.modes.SHORT_BITS);
        op(a68, A68_STD, "LENG", m);
        // BITS operators.
        m = proc(a68, a68.modes.INT, a68.modes.BITS);
        op(a68, A68_STD, "ABS", m);
        m = proc(a68, a68.modes.BITS, a68.modes.INT);
        op(a68, A68_STD, "BIN", m);
        m = proc(a68, a68.modes.BITS, a68.modes.BITS);
        op(a68, A68_STD, "NOT", m);
        op(a68, A68_STD, "~", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.BITS, a68.modes.BITS);
        op(a68, A68_STD, "=", m);
        op(a68, A68_STD, "/=", m);
        op(a68, A68_STD, "<=", m);
        op(a68, A68_STD, ">=", m);
        op(a68, A68_STD, "EQ", m);
        op(a68, A68_STD, "NE", m);
        op(a68, A68_STD, "LE", m);
        op(a68, A68_STD, "GE", m);
        m = proc(a68, a68.modes.BITS, a68.modes.BITS, a68.modes.BITS);
        op(a68, A68_STD, "AND", m);
        op(a68, A68_STD, "OR", m);
        m = proc(a68, a68.modes.BITS, a68.modes.BITS, a68.modes.INT);
        op(a68, A68_STD, "SHL", m);
        op(a68, A68_STD, "UP", m);
        op(a68, A68_STD, "SHR", m);
        op(a68, A68_STD, "DOWN", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.INT, a68.modes.BITS);
        op(a68, A68_STD, "ELEM", m);
        m = proc(a68, a68.modes.LONG_BITS, a68.modes.BITS);
        op(a68, A68_STD, "LENG", m);
        m = proc(a68, a68.modes.SHORT_BITS, a68.modes.BITS);
        op(a68, A68_STD, "SHORTEN", m);
        // LONG BITS operators.
        m = proc(a68, a68.modes.LONG_INT, a68.modes.LONG_BITS);
        op(a68, A68_STD, "ABS", m);
        m = proc(a68, a68.modes.LONG_BITS, a68.modes.LONG_INT);
        op(a68, A68_STD, "BIN", m);
        m = proc(a68, a68.modes.LONG_BITS, a68.modes.LONG_BITS);
        op(a68, A68_STD, "NOT", m);
        op(a68, A68_STD, "~", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.LONG_BITS, a68.modes.LONG_BITS);
        op(a68, A68_STD, "=", m);
        op(a68, A68_STD, "EQ", m);
        op(a68, A68_STD, "/=", m);
        op(a68, A68_STD, "NE", m);
        op(a68, A68_STD, "<=", m);
        op(a68, A68_STD, "LE", m);
        op(a68, A68_STD, ">=", m);
        op(a68, A68_STD, "GE", m);
        m = proc(a68, a68.modes.LONG_BITS, a68.modes.LONG_BITS, a68.modes.LONG_BITS);
        op(a68, A68_STD, "AND", m);
        op(a68, A68_STD, "OR", m);
        m = proc(a68, a68.modes.LONG_BITS, a68.modes.LONG_BITS, a68.modes.INT);
        op(a68, A68_STD, "SHL", m);
        op(a68, A68_STD, "UP", m);
        op(a68, A68_STD, "SHR", m);
        op(a68, A68_STD, "DOWN", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.INT, a68.modes.LONG_BITS);
        op(a68, A68_STD, "ELEM", m);
        m = proc(a68, a68.modes.BITS, a68.modes.LONG_BITS);
        op(a68, A68_STD, "SHORTEN", m);
        m = proc(a68, a68.modes.LONG_LONG_BITS, a68.modes.LONG_BITS);
        op(a68, A68_STD, "LENG", m);
        // LONG LONG BITS operators
        m = proc(a68, a68.modes.BOOL, a68.modes.LONG_LONG_BITS, a68.modes.LONG_LONG_BITS);
        op(a68, A68_STD, "=", m);
        op(a68, A68_STD, "EQ", m);
        op(a68, A68_STD, "/=", m);
        op(a68, A68_STD, "NE", m);
        op(a68, A68_STD, "<=", m);
        op(a68, A68_STD, "LE", m);
        op(a68, A68_STD, ">=", m);
        op(a68, A68_STD, "GE", m);
        m = proc(a68, a68.modes.LONG_LONG_BITS, a68.modes.LONG_LONG_BITS);
        op(a68, A68_STD, "NOT", m);
        op(a68, A68_STD, "~", m);
        m = proc(a68, a68.modes.LONG_LONG_BITS, a68.modes.LONG_LONG_BITS, a68.modes.LONG_LONG_BITS);
        op(a68, A68_STD, "AND", m);
        op(a68, A68_STD, "OR", m);
        m = proc(a68, a68.modes.LONG_LONG_BITS, a68.modes.LONG_LONG_BITS, a68.modes.INT);
        op(a68, A68_STD, "SHL", m);
        op(a68, A68_STD, "UP", m);
        op(a68, A68_STD, "SHR", m);
        op(a68, A68_STD, "DOWN", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.INT, a68.modes.LONG_LONG_BITS);
        op(a68, A68_STD, "ELEM", m);
        m = proc(a68, a68.modes.LONG_LONG_BITS, a68.modes.LONG_LONG_INT);
        op(a68, A68_STD, "BIN", m);
        m = proc(a68, a68.modes.LONG_LONG_INT, a68.modes.LONG_LONG_BITS);
        op(a68, A68_STD, "ABS", m);
        m = proc(a68, a68.modes.LONG_BITS, a68.modes.LONG_LONG_BITS);
        op(a68, A68_STD, "SHORTEN", m);
        // REAL operators.
        m = a68.modeCache.procRealReal;
        op(a68, A68_STD, "+", m);
        op(a68, A68_STD, "-", m);
        op(a68, A68_STD, "ABS", m);
        m = proc(a68, a68.modes.INT, a68.modes.REAL);
        op(a68, A68_STD, "SIGN", m);
        op(a68, A68_STD, "ROUND", m);
        op(a68, A68_STD, "ENTIER", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.REAL, a68.modes.REAL);
        op(a68, A68_STD, "=", m);
        op(a68, A68_STD, "/=", m);
        op(a68, A68_STD, "<", m);
        op(a68, A68_STD, "<=", m);
        op(a68, A68_STD, ">", m);
        op(a68, A68_STD, ">=", m);
        op(a68, A68_STD, "EQ", m);
        op(a68, A68_STD, "NE", m);
        op(a68, A68_STD, "LT", m);
        op(a68, A68_STD, "LE", m);
        op(a68, A68_STD, "GT", m);
        op(a68, A68_STD, "GE", m);
        m = a68.modeCache.procRealRealReal;
        op(a68, A68_STD, "+", m);
        op(a68, A68_STD, "-", m);
        op(a68, A68_STD, "*", m);
        op(a68, A68_STD, "/", m);
        op(a68, A68_STD, "**", m);
        op(a68, A68_STD, "^", m);
        m = proc(a68, a68.modes.REAL, a68.modes.REAL, a68.modes.INT);
        op(a68, A68_STD, "**", m);
        op(a68, A68_STD, "^", m);
        m = proc(a68, a68.modes.REF_REAL, a68.modes.REF_REAL, a68.modes.REAL);
        op(a68, A68_STD, "+:=", m);
        op(a68, A68_STD, "-:=", m);
        op(a68, A68_STD, "*:=", m);
        op(a68, A68_STD, "/:=", m);
        op(a68, A68_STD, "PLUSAB", m);
        op(a68, A68_STD, "MINUSAB", m);
        op(a68, A68_STD, "TIMESAB", m);
        op(a68, A68_STD, "DIVAB", m);
        m = proc(a68, a68.modes.LONG_REAL, a68.modes.REAL);
        op(a68, A68_STD, "LENG", m);
        // LONG REAL operators
        m = proc(a68, a68.modes.LONG_LONG_REAL, a68.modes.LONG_REAL);
        op(a68, A68_STD, "LENG", m);
        m = proc(a68, a68.modes.REAL, a68.modes.LONG_REAL);
        op(a68, A68_STD, "SHORTEN", m);
        m = proc(a68, a68.modes.LONG_REAL, a68.modes.LONG_REAL);
        op(a68, A68_STD, "+", m);
        op(a68, A68_STD, "-", m);
        op(a68, A68_STD, "ABS", m);
        m = proc(a68, a68.modes.INT, a68.modes.LONG_REAL);
        op(a68, A68_STD, "SIGN", m);
        m = proc(a68, a68.modes.LONG_INT, a68.modes.LONG_REAL);
        op(a68, A68_STD, "ENTIER", m);
        op(a68, A68_STD, "ROUND", m);
        m = proc(a68, a68.modes.LONG_REAL, a68.modes.LONG_REAL, a68.modes.LONG_REAL);
        op(a68, A68_STD, "+", m);
        op(a68, A68_STD, "-", m);
        op(a68, A68_STD, "*", m);
        op(a68, A68_STD, "/", m);
        op(a68, A68_STD, "**", m);
        op(a68, A68_STD, "^", m);
        m = proc(a68, a68.modes.REF_LONG_REAL, a68.modes.REF_LONG_REAL, a68.modes.LONG_REAL);
        op(a68, A68_STD, "+:=", m);
        op(a68, A68_STD, "-:=", m);
        op(a68, A68_STD, "*:=", m);
        op(a68, A68_STD, "/:=", m);
        op(a68, A68_STD, "PLUSAB", m);
        op(a68, A68_STD, "MINUSAB", m);
        op(a68, A68_STD, "TIMESAB", m);
        op(a68, A68_STD, "DIVAB", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.LONG_REAL, a68.modes.LONG_REAL);
        op(a68, A68_STD, "=", m);
        op(a68, A68_STD, "EQ", m);
        op(a68, A68_STD, "/=", m);
        op(a68, A68_STD, "NE", m);
        op(a68, A68_STD, "<", m);
        op(a68, A68_STD, "LT", m);
        op(a68, A68_STD, "<=", m);
        op(a68, A68_STD, "LE", m);
        op(a68, A68_STD, ">", m);
        op(a68, A68_STD, "GT", m);
        op(a68, A68_STD, ">=", m);
        op(a68, A68_STD, "GE", m);
        m = proc(a68, a68.modes.LONG_REAL, a68.modes.LONG_REAL, a68.modes.INT);
        op(a68, A68_STD, "**", m);
        op(a68, A68_STD, "^", m);
        // LONG LONG REAL operators.
        m = proc(a68, a68.modes.LONG_REAL, a68.modes.LONG_LONG_REAL);
        op(a68, A68_STD, "SHORTEN", m);
        m = proc(a68, a68.modes.LONG_LONG_REAL, a68.modes.LONG_LONG_REAL);
        op(a68, A68_STD, "ABS", m);
        op(a68, A68_STD, "+", m);
        op(a68, A68_STD, "-", m);
        m = proc(a68, a68.modes.INT, a68.modes.LONG_LONG_REAL);
        op(a68, A68_STD, "SIGN", m);
        m = proc(a68, a68.modes.LONG_LONG_INT, a68.modes.LONG_LONG_REAL);
        op(a68, A68_STD, "ENTIER", m);
        op(a68, A68_STD, "ROUND", m);
        m = proc(a68, a68.modes.LONG_LONG_REAL, a68.modes.LONG_LONG_REAL, a68.modes.LONG_LONG_REAL);
        op(a68, A68_STD, "+", m);
        op(a68, A68_STD, "-", m);
        op(a68, A68_STD, "*", m);
        op(a68, A68_STD, "/", m);
        op(a68, A68_STD, "**", m);
        op(a68, A68_STD, "^", m);
        m = proc(a68, a68.modes.REF_LONG_LONG_REAL, a68.modes.REF_LONG_LONG_REAL, a68.modes.LONG_LONG_REAL);
        op(a68, A68_STD, "+:=", m);
        op(a68, A68_STD, "-:=", m);
        op(a68, A68_STD, "*:=", m);
        op(a68, A68_STD, "/:=", m);
        op(a68, A68_STD, "PLUSAB", m);
        op(a68, A68_STD, "MINUSAB", m);
        op(a68, A68_STD, "TIMESAB", m);
        op(a68, A68_STD, "DIVAB", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.LONG_LONG_REAL, a68.modes.LONG_LONG_REAL);
        op(a68, A68_STD, "=", m);
        op(a68, A68_STD, "EQ", m);
        op(a68, A68_STD, "/=", m);
        op(a68, A68_STD, "NE", m);
        op(a68, A68_STD, "<", m);
        op(a68, A68_STD, "LT", m);
        op(a68, A68_STD, "<=", m);
        op(a68, A68_STD, "LE", m);
        op(a68, A68_STD, ">", m);
        op(a68, A68_STD, "GT", m);
        op(a68, A68_STD, ">=", m);
        op(a68, A68_STD, "GE", m);
        m = proc(a68, a68.modes.LONG_LONG_REAL, a68.modes.LONG_LONG_REAL, a68.modes.INT);
        op(a68, A68_STD, "**", m);
        op(a68, A68_STD, "^", m);
        // ROWS operators.
        m = proc(a68, a68.modes.INT, a68.modes.ROWS);
        op(a68, A68_STD, "LWB", m);
        op(a68, A68_STD, "UPB", m);
        m = proc(a68, a68.modes.INT, a68.modes.INT, a68.modes.ROWS);
        op(a68, A68_STD, "LWB", m);
        op(a68, A68_STD, "UPB", m);
        // BYTES operators.
        m = proc(a68, a68.modes.BYTES, a68.modes.STRING);
        idfUnimpl(a68, A68_STD, "bytespack", m);
        m = proc(a68, a68.modes.CHAR, a68.modes.INT, a68.modes.BYTES);
        opUnimpl(a68, A68_STD, "ELEM", m);
        m = proc(a68, a68.modes.BYTES, a68.modes.BYTES, a68.modes.BYTES);
        opUnimpl(a68, A68_STD, "+", m);
        m = proc(a68, a68.modes.REF_BYTES, a68.modes.REF_BYTES, a68.modes.BYTES);
        opUnimpl(a68, A68_STD, "+:=", m);
        opUnimpl(a68, A68_STD, "PLUSAB", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.BYTES, a68.modes.BYTES);
        opUnimpl(a68, A68_STD, "=", m);
        opUnimpl(a68, A68_STD, "/=", m);
        opUnimpl(a68, A68_STD, "<", m);
        opUnimpl(a68, A68_STD, "<=", m);
        opUnimpl(a68, A68_STD, ">", m);
        opUnimpl(a68, A68_STD, ">=", m);
        opUnimpl(a68, A68_STD, "EQ", m);
        opUnimpl(a68, A68_STD, "NE", m);
        opUnimpl(a68, A68_STD, "LT", m);
        opUnimpl(a68, A68_STD, "LE", m);
        opUnimpl(a68, A68_STD, "GT", m);
        opUnimpl(a68, A68_STD, "GE", m);
        // LONG BYTES operators.
        m = proc(a68, a68.modes.LONG_BYTES, a68.modes.BYTES);
        opUnimpl(a68, A68_STD, "LENG", m);
        m = proc(a68, a68.modes.BYTES, a68.modes.LONG_BYTES);
        idfUnimpl(a68, A68_STD, "SHORTEN", m);
        m = proc(a68, a68.modes.LONG_BYTES, a68.modes.STRING);
        idfUnimpl(a68, A68_STD, "longbytespack", m);
        m = proc(a68, a68.modes.CHAR, a68.modes.INT, a68.modes.LONG_BYTES);
        opUnimpl(a68, A68_STD, "ELEM", m);
        m = proc(a68, a68.modes.LONG_BYTES, a68.modes.LONG_BYTES, a68.modes.LONG_BYTES);
        opUnimpl(a68, A68_STD, "+", m);
        m = proc(a68, a68.modes.REF_LONG_BYTES, a68.modes.REF_LONG_BYTES, a68.modes.LONG_BYTES);
        opUnimpl(a68, A68_STD, "+:=", m);
        opUnimpl(a68, A68_STD, "PLUSAB", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.LONG_BYTES, a68.modes.LONG_BYTES);
        opUnimpl(a68, A68_STD, "=", m);
        opUnimpl(a68, A68_STD, "/=", m);
        opUnimpl(a68, A68_STD, "<", m);
        opUnimpl(a68, A68_STD, "<=", m);
        opUnimpl(a68, A68_STD, ">", m);
        opUnimpl(a68, A68_STD, ">=", m);
        opUnimpl(a68, A68_STD, "EQ", m);
        opUnimpl(a68, A68_STD, "NE", m);
        opUnimpl(a68, A68_STD, "LT", m);
        opUnimpl(a68, A68_STD, "LE", m);
        opUnimpl(a68, A68_STD, "GT", m);
        opUnimpl(a68, A68_STD, "GE", m);
        // COMPLEX operators.
        m = proc(a68, a68.modes.COMPLEX, a68.modes.REAL, a68.modes.REAL);
        op(a68, A68_STD, "I", m);
        op(a68, A68_STD, "+*", m);
        m = proc(a68, a68.modes.COMPLEX, a68.modes.INT, a68.modes.INT);
        op(a68, A68_STD, "I", m);
        op(a68, A68_STD, "+*", m);
        m = proc(a68, a68.modes.REAL, a68.modes.COMPLEX);
        op(a68, A68_STD, "RE", m);
        op(a68, A68_STD, "IM", m);
        m = proc(a68, a68.modes.COMPLEX, a68.modes.COMPLEX, a68.modes.INT);
        opUnimpl(a68, A68_STD, "**", m);
        opUnimpl(a68, A68_STD, "UP", m);
        opUnimpl(a68, A68_STD, "^", m);
        m = proc(a68, a68.modes.COMPLEX, a68.modes.COMPLEX);
        opUnimpl(a68, A68_STD, "SHORTEN", m);
        // LONG COMPLEX  operators
        m = proc(a68, a68.modes.LONG_COMPLEX, a68.modes.LONG_INT, a68.modes.LONG_INT);
        op(a68, A68_STD, "I", m);
        op(a68, A68_STD, "+*", m);
        m = proc(a68, a68.modes.LONG_COMPLEX, a68.modes.LONG_REAL, a68.modes.LONG_REAL);
        op(a68, A68_STD, "I", m);
        op(a68, A68_STD, "+*", m);
        m = proc(a68, a68.modes.LONG_LONG_COMPLEX, a68.modes.LONG_COMPLEX);
        opUnimpl(a68, A68_STD, "LENG", m);
        m = proc(a68, a68.modes.LONG_COMPLEX, a68.modes.LONG_LONG_COMPLEX);
        opUnimpl(a68, A68_STD, "SHORTEN", m);
        m = proc(a68, a68.modes.LONG_COMPLEX, a68.modes.COMPLEX);
        opUnimpl(a68, A68_STD, "LENG", m);
        m = proc(a68, a68.modes.COMPLEX, a68.modes.LONG_COMPLEX);
        opUnimpl(a68, A68_STD, "SHORTEN", m);
        m = proc(a68, a68.modes.LONG_REAL, a68.modes.LONG_COMPLEX);
        op(a68, A68_STD, "RE", m);
        op(a68, A68_STD, "IM", m);
        m = proc(a68, a68.modes.LONG_COMPLEX, a68.modes.LONG_COMPLEX, a68.modes.INT);
        opUnimpl(a68, A68_STD, "**", m);
        opUnimpl(a68, A68_STD, "UP", m);
        opUnimpl(a68, A68_STD, "^", m);
        // LONG LONG COMPLEX operators.
        m = proc(a68, a68.modes.LONG_LONG_COMPLEX, a68.modes.LONG_LONG_INT, a68.modes.LONG_LONG_INT);
        op(a68, A68_STD, "I", m);
        op(a68, A68_STD, "+*", m);
        m = proc(a68, a68.modes.LONG_LONG_COMPLEX, a68.modes.LONG_LONG_REAL, a68.modes.LONG_LONG_REAL);
        op(a68, A68_STD, "I", m);
        op(a68, A68_STD, "+*", m);
        m = proc(a68, a68.modes.LONG_LONG_REAL, a68.modes.LONG_LONG_COMPLEX);
        op(a68, A68_STD, "RE", m);
        op(a68, A68_STD, "IM", m);
        m = proc(a68, a68.modes.LONG_LONG_COMPLEX, a68.modes.LONG_LONG_COMPLEX, a68.modes.INT);
        opUnimpl(a68, A68_STD, "**", m);
        opUnimpl(a68, A68_STD, "UP", m);
        opUnimpl(a68, A68_STD, "^", m);
        m = proc(a68, a68.modes.LONG_LONG_COMPLEX, a68.modes.LONG_LONG_COMPLEX);
        opUnimpl(a68, A68_STD, "LENG", m);
        // SEMA operators.
        m = proc(a68, a68.modes.SEMA, a68.modes.INT);
        opUnimpl(a68, A68_STD, "LEVEL", m);
        m = proc(a68, a68.modes.INT, a68.modes.SEMA);
        opUnimpl(a68, A68_STD, "LEVEL", m);
        m = proc(a68, a68.modes.VOID, a68.modes.SEMA);
        opUnimpl(a68, A68_STD, "UP", m);
        opUnimpl(a68, A68_STD, "DOWN", m);

        // Load Algol 68 parts.
        if (!a68.options().buildingLibga68) {
            Extractor.extractRevelation(a68, a68.standenv, a68.job.topNode.info.line,
                    "STANDARD", "ga68");
        }
    }

    /** gnu_prelude: GNU extensions for the standenv. */
    private static void gnuPrelude(A68 a68) {
        // GCC-specific, omitted: the lowerer arguments (a68_lower_*) of a68_idf and a68_op.
        Moid m = null;
        // Modes.
        mode(a68, 100, "INT", Ref.of(() -> a68.modes.WORD_INT, v -> a68.modes.WORD_INT = v));
        mode(a68, 100, "BITS", Ref.of(() -> a68.modes.WORD_BITS, v -> a68.modes.WORD_BITS = v));
        mode(a68, 100, "BYTES", Ref.of(() -> a68.modes.WORD_BYTES, v -> a68.modes.WORD_BYTES = v));
        mode(a68, 100, "REAL", Ref.of(() -> a68.modes.WORD_REAL, v -> a68.modes.WORD_REAL = v));

        mode(a68, 100, "COMPL", Ref.of(() -> a68.modes.WORD_COMPLEX, v -> a68.modes.WORD_COMPLEX = v));
        Ref<Pack> z = Ref.box(null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.WORD_REAL, Keywords.addToken(a68, "im").text, null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.WORD_REAL, Keywords.addToken(a68, "re").text, null);
        m = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), STRUCT_SYMBOL, ModeBuilder.countPackMembers(a68, z.get()), null, null, z.get());
        a68.modes.WORD_COMPLEX.equivalentMode = m;

        a68.modes.REF_WORD_INT = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.WORD_INT, null);
        a68.modes.REF_WORD_REAL = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.WORD_REAL, null);

        a68.modes.REF_WORD_COMPLEX = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), REF_SYMBOL, 0, null, a68.modes.WORD_COMPLEX, null);
        z.set(null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.REF_WORD_REAL, Keywords.addToken(a68, "im").text, null);
        ModeBuilder.addModeToPack(a68, z, a68.modes.REF_WORD_REAL, Keywords.addToken(a68, "re").text, null);
        m = ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), STRUCT_SYMBOL, ModeBuilder.countPackMembers(a68, z.get()), null, null, z.get());
        a68.modes.REF_WORD_COMPLEX.name = m;

        // Priorities.
        prio(a68, "ELEMS", 8);
        prio(a68, "SET", 7);
        prio(a68, "CLEAR", 7);
        prio(a68, "TEST", 7);
        // Identifiers.
        idf(a68, A68_EXT, "infinity", a68.modes.REAL);
        idf(a68, A68_EXT, "minusinfinity", a68.modes.REAL);
        idfUnimpl(a68, A68_EXT, "longlonginfinity", a68.modes.LONG_LONG_REAL);
        idfUnimpl(a68, A68_EXT, "longlongminusinfinity", a68.modes.LONG_LONG_REAL);
        idfUnimpl(a68, A68_EXT, "longinfinity", a68.modes.LONG_REAL);
        idfUnimpl(a68, A68_EXT, "longminusinfinity", a68.modes.LONG_REAL);
        idf(a68, A68_EXT, "minint", a68.modes.INT);
        idf(a68, A68_EXT, "longminint", a68.modes.LONG_INT);
        idf(a68, A68_EXT, "longlongminint", a68.modes.LONG_LONG_INT);
        idf(a68, A68_EXT, "shortminint", a68.modes.SHORT_INT);
        idf(a68, A68_EXT, "shortshortminint", a68.modes.SHORT_SHORT_INT);
        idf(a68, A68_EXT, "wordminint", a68.modes.WORD_INT);
        idf(a68, A68_EXT, "minreal", a68.modes.REAL);
        idf(a68, A68_EXT, "longminreal", a68.modes.LONG_REAL);
        idf(a68, A68_EXT, "longlongminreal", a68.modes.LONG_LONG_REAL);
        idf(a68, A68_EXT, "wordminreal", a68.modes.WORD_REAL);
        idf(a68, A68_EXT, "eofchar", a68.modes.CHAR);
        idf(a68, A68_EXT, "replacementchar", a68.modes.CHAR);
        idf(a68, A68_EXT, "wordmaxbits", a68.modes.WORD_BITS);
        idf(a68, A68_EXT, "wordbitswidth", a68.modes.INT);
        idf(a68, A68_EXT, "wordmaxreal", a68.modes.WORD_REAL);
        idf(a68, A68_EXT, "wordsmallreal", a68.modes.WORD_REAL);
        idf(a68, A68_EXT, "wordpi", a68.modes.WORD_REAL);
        // REAL procedures.
        m = a68.modeCache.procRealReal;
        idf(a68, A68_EXT, "log", m);
        // LONG REAL procedures.
        m = proc(a68, a68.modes.LONG_REAL, a68.modes.LONG_REAL);
        idf(a68, A68_EXT, "longlog", m);
        // LONG LONG REAL procedures.
        m = proc(a68, a68.modes.LONG_LONG_REAL, a68.modes.LONG_LONG_REAL);
        idf(a68, A68_EXT, "longlonglog", m);
        // WORD REAL procedures.
        m = proc(a68, a68.modes.WORD_REAL, a68.modes.WORD_REAL);
        idf(a68, A68_EXT, "wordarccos", m);
        idf(a68, A68_EXT, "wordarcsin", m);
        idf(a68, A68_EXT, "wordarctan", m);
        idf(a68, A68_EXT, "wordcos", m);
        idf(a68, A68_EXT, "wordexp", m);
        idf(a68, A68_EXT, "wordln", m);
        idf(a68, A68_EXT, "wordsin", m);
        idf(a68, A68_EXT, "wordsqrt", m);
        idf(a68, A68_EXT, "wordtan", m);
        idf(a68, A68_EXT, "wordlog", m);
        // BOOL operators.
        m = proc(a68, a68.modes.BOOL, a68.modes.BOOL, a68.modes.BOOL);
        op(a68, A68_EXT, "XOR", m);
        // SHORT SHORT BITS operators.
        m = proc(a68, a68.modes.SHORT_SHORT_BITS, a68.modes.SHORT_SHORT_BITS, a68.modes.SHORT_SHORT_BITS);
        op(a68, A68_EXT, "XOR", m);
        // SHORT BITS operators.
        m = proc(a68, a68.modes.SHORT_BITS, a68.modes.SHORT_BITS, a68.modes.SHORT_BITS);
        op(a68, A68_EXT, "XOR", m);
        // BITS operators.
        m = proc(a68, a68.modes.BITS, a68.modes.BITS, a68.modes.BITS);
        op(a68, A68_EXT, "XOR", m);
        // LONG BITS operators.
        m = proc(a68, a68.modes.LONG_BITS, a68.modes.LONG_BITS, a68.modes.LONG_BITS);
        op(a68, A68_EXT, "XOR", m);
        // LONG LONG BITS operators.
        m = proc(a68, a68.modes.LONG_LONG_BITS, a68.modes.LONG_LONG_BITS, a68.modes.LONG_LONG_BITS);
        op(a68, A68_EXT, "XOR", m);
        // ROWS operators.
        m = proc(a68, a68.modes.INT, a68.modes.ROWS);
        op(a68, A68_EXT, "ELEMS", m);
        m = proc(a68, a68.modes.INT, a68.modes.INT, a68.modes.ROWS);
        op(a68, A68_EXT, "ELEMS", m);
        // WORD INT operators
        m = proc(a68, a68.modes.WORD_INT, a68.modes.WORD_INT);
        op(a68, A68_EXT, "+", m);
        op(a68, A68_EXT, "-", m);
        op(a68, A68_EXT, "ABS", m);
        m = proc(a68, a68.modes.INT, a68.modes.WORD_INT);
        op(a68, A68_EXT, "SIGN", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.WORD_INT);
        op(a68, A68_EXT, "ODD", m);
        m = proc(a68, a68.modes.WORD_INT, a68.modes.WORD_INT, a68.modes.WORD_INT);
        op(a68, A68_EXT, "+", m);
        op(a68, A68_EXT, "-", m);
        op(a68, A68_EXT, "*", m);
        op(a68, A68_EXT, "OVER", m);
        op(a68, A68_EXT, "%", m);
        op(a68, A68_EXT, "MOD", m);
        op(a68, A68_EXT, "%*", m);
        m = proc(a68, a68.modes.REF_WORD_INT, a68.modes.REF_WORD_INT, a68.modes.WORD_INT);
        op(a68, A68_EXT, "+:=", m);
        op(a68, A68_EXT, "-:=", m);
        op(a68, A68_EXT, "*:=", m);
        op(a68, A68_EXT, "%:=", m);
        op(a68, A68_EXT, "%*:=", m);
        op(a68, A68_EXT, "PLUSAB", m);
        op(a68, A68_EXT, "MINUSAB", m);
        op(a68, A68_EXT, "TIMESAB", m);
        op(a68, A68_EXT, "OVERAB", m);
        op(a68, A68_EXT, "MODAB", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.WORD_INT, a68.modes.WORD_INT);
        op(a68, A68_EXT, "=", m);
        op(a68, A68_EXT, "EQ", m);
        op(a68, A68_EXT, "/=", m);
        op(a68, A68_EXT, "NE", m);
        op(a68, A68_EXT, "<", m);
        op(a68, A68_EXT, "LT", m);
        op(a68, A68_EXT, "<=", m);
        op(a68, A68_EXT, "LE", m);
        op(a68, A68_EXT, ">", m);
        op(a68, A68_EXT, "GT", m);
        op(a68, A68_EXT, ">=", m);
        op(a68, A68_EXT, "GE", m);
        m = proc(a68, a68.modes.WORD_REAL, a68.modes.WORD_INT, a68.modes.WORD_INT);
        op(a68, A68_EXT, "/", m);
        m = proc(a68, a68.modes.WORD_INT, a68.modes.WORD_INT, a68.modes.INT);
        op(a68, A68_EXT, "**", m);
        op(a68, A68_EXT, "^", m);
        // SHORT SHORT BITS operators.
        m = proc(a68, a68.modes.SHORT_SHORT_BITS, a68.modes.SHORT_SHORT_BITS, a68.modes.INT);
        op(a68, A68_EXT, "SET", m);
        op(a68, A68_EXT, "CLEAR", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.SHORT_SHORT_BITS, a68.modes.INT);
        op(a68, A68_EXT, "TEST", m);
        // SHORT BITS operators.
        m = proc(a68, a68.modes.SHORT_BITS, a68.modes.SHORT_BITS, a68.modes.INT);
        op(a68, A68_EXT, "SET", m);
        op(a68, A68_EXT, "CLEAR", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.SHORT_BITS, a68.modes.INT);
        op(a68, A68_EXT, "TEST", m);
        // BITS operators.
        m = proc(a68, a68.modes.BITS, a68.modes.BITS, a68.modes.INT);
        op(a68, A68_EXT, "SET", m);
        op(a68, A68_EXT, "CLEAR", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.BITS, a68.modes.INT);
        op(a68, A68_EXT, "TEST", m);
        // LONG BITS operators.
        m = proc(a68, a68.modes.LONG_BITS, a68.modes.LONG_BITS, a68.modes.INT);
        op(a68, A68_EXT, "SET", m);
        op(a68, A68_EXT, "CLEAR", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.LONG_BITS, a68.modes.INT);
        op(a68, A68_EXT, "TEST", m);
        // LONG LONG BITS operators.
        m = proc(a68, a68.modes.LONG_LONG_BITS, a68.modes.LONG_LONG_BITS, a68.modes.INT);
        op(a68, A68_EXT, "SET", m);
        op(a68, A68_EXT, "CLEAR", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.LONG_LONG_BITS, a68.modes.INT);
        op(a68, A68_EXT, "TEST", m);
        // WORD BITS operators.
        m = proc(a68, a68.modes.BOOL, a68.modes.WORD_BITS, a68.modes.WORD_BITS);
        op(a68, A68_EXT, "=", m);
        op(a68, A68_EXT, "EQ", m);
        op(a68, A68_EXT, "/=", m);
        op(a68, A68_EXT, "NE", m);
        op(a68, A68_EXT, "<=", m);
        op(a68, A68_EXT, "LE", m);
        op(a68, A68_EXT, ">=", m);
        op(a68, A68_EXT, "GE", m);
        m = proc(a68, a68.modes.WORD_BITS, a68.modes.WORD_BITS);
        op(a68, A68_EXT, "NOT", m);
        op(a68, A68_EXT, "~", m);
        m = proc(a68, a68.modes.WORD_BITS, a68.modes.WORD_BITS, a68.modes.WORD_BITS);
        op(a68, A68_EXT, "AND", m);
        op(a68, A68_EXT, "OR", m);
        op(a68, A68_EXT, "XOR", m);
        m = proc(a68, a68.modes.WORD_BITS, a68.modes.WORD_BITS, a68.modes.INT);
        op(a68, A68_EXT, "SHL", m);
        op(a68, A68_EXT, "UP", m);
        op(a68, A68_EXT, "SHR", m);
        op(a68, A68_EXT, "DOWN", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.INT, a68.modes.WORD_BITS);
        op(a68, A68_EXT, "ELEM", m);
        m = proc(a68, a68.modes.WORD_BITS, a68.modes.WORD_INT);
        op(a68, A68_EXT, "BIN", m);
        m = proc(a68, a68.modes.WORD_INT, a68.modes.WORD_BITS);
        op(a68, A68_EXT, "ABS", m);
        m = proc(a68, a68.modes.WORD_BITS, a68.modes.WORD_BITS, a68.modes.INT);
        op(a68, A68_EXT, "SET", m);
        op(a68, A68_EXT, "CLEAR", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.WORD_BITS, a68.modes.INT);
        op(a68, A68_EXT, "TEST", m);
        // REAL operators.
        m = proc(a68, a68.modes.INT, a68.modes.REAL);
        op(a68, A68_EXT, "FLOOR", m);
        // LONG REAL operators.
        m = proc(a68, a68.modes.LONG_INT, a68.modes.LONG_REAL);
        op(a68, A68_EXT, "FLOOR", m);
        // LONG REAL operators.
        m = proc(a68, a68.modes.LONG_LONG_INT, a68.modes.LONG_LONG_REAL);
        op(a68, A68_EXT, "FLOOR", m);
        // WORD REAL operators.
        m = proc(a68, a68.modes.WORD_REAL, a68.modes.WORD_REAL);
        op(a68, A68_EXT, "+", m);
        op(a68, A68_EXT, "-", m);
        op(a68, A68_EXT, "ABS", m);
        m = proc(a68, a68.modes.INT, a68.modes.WORD_REAL);
        op(a68, A68_EXT, "SIGN", m);
        m = proc(a68, a68.modes.WORD_INT, a68.modes.WORD_REAL);
        op(a68, A68_EXT, "ENTIER", m);
        op(a68, A68_EXT, "ROUND", m);
        op(a68, A68_EXT, "FLOOR", m);
        m = proc(a68, a68.modes.WORD_REAL, a68.modes.WORD_REAL, a68.modes.WORD_REAL);
        op(a68, A68_EXT, "+", m);
        op(a68, A68_EXT, "-", m);
        op(a68, A68_EXT, "*", m);
        op(a68, A68_EXT, "/", m);
        op(a68, A68_EXT, "**", m);
        op(a68, A68_EXT, "^", m);
        m = proc(a68, a68.modes.REF_WORD_REAL, a68.modes.REF_WORD_REAL, a68.modes.WORD_REAL);
        op(a68, A68_EXT, "+:=", m);
        op(a68, A68_EXT, "-:=", m);
        op(a68, A68_EXT, "*:=", m);
        op(a68, A68_EXT, "/:=", m);
        op(a68, A68_EXT, "PLUSAB", m);
        op(a68, A68_EXT, "MINUSAB", m);
        op(a68, A68_EXT, "TIMESAB", m);
        op(a68, A68_EXT, "DIVAB", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.WORD_REAL, a68.modes.WORD_REAL);
        op(a68, A68_EXT, "=", m);
        op(a68, A68_EXT, "EQ", m);
        op(a68, A68_EXT, "/=", m);
        op(a68, A68_EXT, "NE", m);
        op(a68, A68_EXT, "<", m);
        op(a68, A68_EXT, "LT", m);
        op(a68, A68_EXT, "<=", m);
        op(a68, A68_EXT, "LE", m);
        op(a68, A68_EXT, ">", m);
        op(a68, A68_EXT, "GT", m);
        op(a68, A68_EXT, ">=", m);
        op(a68, A68_EXT, "GE", m);
        m = proc(a68, a68.modes.WORD_REAL, a68.modes.WORD_REAL, a68.modes.INT);
        op(a68, A68_EXT, "**", m);
        op(a68, A68_EXT, "^", m);
        // WORD COMPLEX  operators
        m = proc(a68, a68.modes.WORD_COMPLEX, a68.modes.WORD_INT, a68.modes.WORD_INT);
        op(a68, A68_STD, "I", m);
        op(a68, A68_STD, "+*", m);
        m = proc(a68, a68.modes.WORD_COMPLEX, a68.modes.WORD_REAL, a68.modes.WORD_REAL);
        op(a68, A68_STD, "I", m);
        op(a68, A68_STD, "+*", m);
        m = proc(a68, a68.modes.WORD_REAL, a68.modes.WORD_COMPLEX);
        op(a68, A68_STD, "RE", m);
        op(a68, A68_STD, "IM", m);
        opUnimpl(a68, A68_STD, "ARG", m);
        opUnimpl(a68, A68_STD, "ABS", m);
        m = proc(a68, a68.modes.WORD_COMPLEX, a68.modes.WORD_COMPLEX);
        opUnimpl(a68, A68_STD, "+", m);
        opUnimpl(a68, A68_STD, "-", m);
        op(a68, A68_STD, "CONJ", m);
        m = proc(a68, a68.modes.WORD_COMPLEX, a68.modes.WORD_COMPLEX, a68.modes.WORD_COMPLEX);
        opUnimpl(a68, A68_STD, "+", m);
        opUnimpl(a68, A68_STD, "-", m);
        opUnimpl(a68, A68_STD, "*", m);
        opUnimpl(a68, A68_STD, "/", m);
        m = proc(a68, a68.modes.WORD_COMPLEX, a68.modes.WORD_COMPLEX, a68.modes.INT);
        opUnimpl(a68, A68_STD, "**", m);
        opUnimpl(a68, A68_STD, "UP", m);
        opUnimpl(a68, A68_STD, "^", m);
        m = proc(a68, a68.modes.BOOL, a68.modes.WORD_COMPLEX, a68.modes.WORD_COMPLEX);
        opUnimpl(a68, A68_STD, "=", m);
        opUnimpl(a68, A68_STD, "EQ", m);
        opUnimpl(a68, A68_STD, "/=", m);
        opUnimpl(a68, A68_STD, "NE", m);
        m = proc(a68, a68.modes.REF_WORD_COMPLEX, a68.modes.REF_WORD_COMPLEX, a68.modes.WORD_COMPLEX);
        opUnimpl(a68, A68_STD, "+:=", m);
        opUnimpl(a68, A68_STD, "-:=", m);
        opUnimpl(a68, A68_STD, "*:=", m);
        opUnimpl(a68, A68_STD, "/:=", m);
        opUnimpl(a68, A68_STD, "PLUSAB", m);
        opUnimpl(a68, A68_STD, "MINUSAB", m);
        opUnimpl(a68, A68_STD, "TIMESAB", m);
        opUnimpl(a68, A68_STD, "DIVAB", m);
    }

    /** posix_prelude: POSIX prelude. */
    private static void posixPrelude(A68 a68) {
        if (!a68.options().buildingLibga68) {
            Extractor.extractRevelation(a68, a68.standenv, a68.job.topNode.info.line,
                    "POSIX", "ga68");
        }
    }

    /** a68_make_standard_environ */
    public static void makeStandardEnviron(A68 a68) {
        standMoids(a68);
        a68.modeCache.procBool = proc(a68, a68.modes.BOOL);
        a68.modeCache.procChar = proc(a68, a68.modes.CHAR);
        a68.modeCache.procComplexComplex = proc(a68, a68.modes.COMPLEX, a68.modes.COMPLEX);
        a68.modeCache.procInt = proc(a68, a68.modes.INT);
        a68.modeCache.procIntInt = proc(a68, a68.modes.INT, a68.modes.INT);
        a68.modeCache.procIntIntReal = proc(a68, a68.modes.REAL, a68.modes.INT, a68.modes.INT);
        a68.modeCache.procIntReal = proc(a68, a68.modes.REAL, a68.modes.INT);
        a68.modeCache.procIntRealReal = proc(a68, a68.modes.REAL, a68.modes.INT, a68.modes.REAL);
        a68.modeCache.procIntRealRealReal = proc(a68, a68.modes.REAL, a68.modes.INT, a68.modes.REAL, a68.modes.REAL);
        a68.modeCache.procReal = proc(a68, a68.modes.REAL);
        a68.modeCache.procRealIntReal = proc(a68, a68.modes.REAL, a68.modes.REAL, a68.modes.INT);
        a68.modeCache.procRealRealIntReal = proc(a68, a68.modes.REAL, a68.modes.REAL, a68.modes.REAL, a68.modes.INT);
        a68.modeCache.procRealReal = a68.modes.PROC_REAL_REAL;
        a68.modeCache.procRealRealReal = proc(a68, a68.modes.REAL, a68.modes.REAL, a68.modes.REAL);
        a68.modeCache.procRealRealRealInt = proc(a68, a68.modes.INT, a68.modes.REAL, a68.modes.REAL, a68.modes.REAL);
        a68.modeCache.procRealRealRealReal = proc(a68, a68.modes.REAL, a68.modes.REAL, a68.modes.REAL, a68.modes.REAL);
        a68.modeCache.procRealRealRealRealReal = proc(a68, a68.modes.REAL, a68.modes.REAL, a68.modes.REAL, a68.modes.REAL, a68.modes.REAL);
        a68.modeCache.procRealRealRealRealRealReal = proc(a68, a68.modes.REAL, a68.modes.REAL, a68.modes.REAL, a68.modes.REAL, a68.modes.REAL, a68.modes.REAL);
        a68.modeCache.procRealRefRealRefIntVoid = proc(a68, a68.modes.VOID, a68.modes.REAL, a68.modes.REF_REAL, a68.modes.REF_INT);
        a68.modeCache.procVoid = proc(a68, a68.modes.VOID);
        standPrelude(a68);
        if (!a68.options().strict) {
            gnuPrelude(a68);
            posixPrelude(a68);
        }
    }
}
