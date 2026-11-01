// Java port of ga68's a68-moids-misc.cc: miscellaneous MOID routines.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * Miscellaneous MOID routines (a68-moids-misc.cc): mode checker helpers,
 * coercibility predicates, balancing and coercion insertion.
 *
 * <h2>Porting notes</h2>
 * <ul>
 * <li>Deflexing strategies stay {@code int}; the constants of the C enum in
 * a68.h are defined here ({@link #NO_DEFLEXING} ... {@link #SKIP_DEFLEXING}).</li>
 * <li>Sorts ({@code int sort}, {@code int c}) and attributes are
 * {@link Attribute}s; {@code MOID_T **} / {@code SOID_T **} are {@link Ref}s,
 * {@code bool *} out-parameters are {@code boolean[]} of length 1.</li>
 * <li>The macros {@code IS_INTEGRAL}, {@code IS_BITS}, {@code IS_REAL} and
 * {@code A68_IF_MODE_IS_WELL} have no shared Java equivalent yet and are
 * private helpers here.</li>
 * </ul>
 * Quirks of the original are preserved on purpose and marked with "Sic".
 */
public final class MoidsMisc {
    private MoidsMisc() {}

    // Deflexing strategies (enum in a68.h).

    /** NO_DEFLEXING (a68.h). */
    public static final int NO_DEFLEXING = 1;
    /** SAFE_DEFLEXING (a68.h). */
    public static final int SAFE_DEFLEXING = 2;
    /** ALIAS_DEFLEXING (a68.h). */
    public static final int ALIAS_DEFLEXING = 3;
    /** FORCE_DEFLEXING (a68.h). */
    public static final int FORCE_DEFLEXING = 4;
    /** SKIP_DEFLEXING (a68.h). */
    public static final int SKIP_DEFLEXING = 5;

    /** A68_DEPREF (a68.h). */
    public static final boolean A68_DEPREF = true;
    /** A68_NO_DEPREF (a68.h). */
    public static final boolean A68_NO_DEPREF = false;

    // Macros of a68.h / a68-types.h used in this file.

    /** A68_IF_MODE_IS_WELL (n). */
    private static boolean ifModeIsWell(A68 a68, Moid n) {
        return !(n == a68.modes.ERROR || n == a68.modes.UNDEFINED);
    }

    /** IS_INTEGRAL (m). */
    private static boolean isIntegral(A68 a68, Moid m) {
        return (m == a68.modes.INT
                || m == a68.modes.LONG_INT
                || m == a68.modes.LONG_LONG_INT
                || m == a68.modes.SHORT_INT
                || m == a68.modes.SHORT_SHORT_INT
                || m == a68.modes.WORD_INT);
    }

    /** IS_BITS (m). */
    private static boolean isBits(A68 a68, Moid m) {
        return (m == a68.modes.BITS
                || m == a68.modes.LONG_BITS
                || m == a68.modes.LONG_LONG_BITS
                || m == a68.modes.SHORT_BITS
                || m == a68.modes.SHORT_SHORT_BITS
                || m == a68.modes.WORD_BITS);
    }

    /** IS_REAL (m). */
    private static boolean isReal(A68 a68, Moid m) {
        return (m == a68.modes.REAL
                || m == a68.modes.LONG_REAL
                || m == a68.modes.LONG_LONG_REAL
                || m == a68.modes.WORD_REAL);
    }

    /** The mode list of the job, {@code &TOP_MOID (&A68_JOB)}. */
    private static Ref<Moid> topMoidRef(A68 a68) {
        return Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v);
    }

    /*
     * MODE checker routines.
     */

    /** Absorb nested series modes recursively (a68_absorb_series_pack). */
    public static void absorbSeriesPack(A68 a68, Ref<Moid> p) {
        boolean siga;

        do {
            Ref.Box<Pack> z = Ref.box(null);

            siga = false;
            for (Pack t = p.get().pack; t != null; t = t.next) {
                if (t.type != null && t.type.attribute == SERIES_MODE) {
                    siga = true;
                    for (Pack s = t.type.pack; s != null; s = s.next) {
                        ModeBuilder.addModeToPack(a68, z, s.type, null, s.node);
                    }
                } else {
                    ModeBuilder.addModeToPack(a68, z, t.type, null, t.node);
                }
            }
            p.get().pack = z.v;
        } while (siga);
    }

    /** Make SERIES (u, v) (a68_make_series_from_moids). */
    public static Moid makeSeriesFromMoids(A68 a68, Moid u, Moid v) {
        Ref.Box<Moid> x = Ref.box(Parser.newMoid(a68));

        x.v.attribute = SERIES_MODE;
        ModeBuilder.addModeToPack(a68, Ref.of(() -> x.v.pack, w -> x.v.pack = w), u, null, u.node);
        ModeBuilder.addModeToPack(a68, Ref.of(() -> x.v.pack, w -> x.v.pack = w), v, null, v.node);
        absorbSeriesPack(a68, x);
        x.v.dim = ModeBuilder.countPackMembers(a68, x.v.pack);
        // (void): the result is discarded; X is returned even if an equivalent
        // mode was already registered.
        ModeBuilder.registerExtraMode(a68, topMoidRef(a68), x.v);
        if (x.v.dim == 1) {
            return x.v.pack.type;
        } else {
            return x.v;
        }
    }

    /**
     * Absorb firmly related unions in mode (a68_absorb_related_subsets).
     *
     * <p>For instance invalid UNION (PROC REF UNION (A, B), A, B) -> valid
     * UNION (A, B), which is used in balancing conformity clauses.
     */
    public static Moid absorbRelatedSubsets(A68 a68, Moid m) {
        // For instance invalid UNION (PROC REF UNION (A, B), A, B) -> valid UNION
        // (A, B), which is used in balancing conformity clauses.
        boolean siga;

        do {
            Ref.Box<Pack> u = Ref.box(null);

            siga = false;
            for (Pack v = m.pack; v != null; v = v.next) {
                Moid n = deprefCompletely(a68, v.type);

                if (n.attribute == UNION_SYMBOL && isSubset(a68, n, m, SAFE_DEFLEXING)) {
                    // Unpack it.
                    for (Pack w = n.pack; w != null; w = w.next) {
                        ModeBuilder.addModeToPack(a68, u, w.type, null, w.node);
                    }
                    siga = true;
                } else {
                    ModeBuilder.addModeToPack(a68, u, v.type, null, v.node);
                }
            }
            m.pack = ModeBuilder.absorbUnionPack(a68, u.v);
        } while (siga);
        return m;
    }

    /** Absorb nested series and united modes recursively (a68_absorb_series_union_pack). */
    public static void absorbSeriesUnionPack(A68 a68, Ref<Moid> p) {
        boolean siga;

        do {
            Ref.Box<Pack> z = Ref.box(null);

            siga = false;
            for (Pack t = p.get().pack; t != null; t = t.next) {
                if (t.type != null && (t.type.attribute == SERIES_MODE || t.type.attribute == UNION_SYMBOL)) {
                    siga = true;
                    for (Pack s = t.type.pack; s != null; s = s.next) {
                        ModeBuilder.addModeToPack(a68, z, s.type, null, s.node);
                    }
                } else {
                    ModeBuilder.addModeToPack(a68, z, t.type, null, t.node);
                }
            }
            p.get().pack = z.v;
        } while (siga);
    }

    /** Make united mode, from mode that is a SERIES (..) (a68_make_united_mode). */
    public static Moid makeUnitedMode(A68 a68, Moid m) {
        if (m == null) {
            return a68.modes.ERROR;
        } else if (m.attribute != SERIES_MODE) {
            return m;
        }

        // Do not unite a single UNION.
        if (m.dim == 1 && m.pack.type.attribute == UNION_SYMBOL) {
            return m.pack.type;
        }

        // Straighten the series.
        Ref.Box<Moid> mm = Ref.box(m);
        absorbSeriesUnionPack(a68, mm);
        m = mm.v;
        // Copy the series into a UNION.
        Ref.Box<Moid> u = Ref.box(Parser.newMoid(a68));
        u.v.attribute = UNION_SYMBOL;
        u.v.pack = null;
        for (Pack w = m.pack; w != null; w = w.next) {
            // Sic: NODE (m), not NODE (w).
            ModeBuilder.addModeToPack(a68, Ref.of(() -> u.v.pack, v -> u.v.pack = v), w.type, null, m.node);
        }

        // Absorb and contract the new UNION.
        absorbSeriesUnionPack(a68, u);
        u.v.dim = ModeBuilder.countPackMembers(a68, u.v.pack);
        u.v.pack = ModeBuilder.absorbUnionPack(a68, u.v.pack);
        ModeBuilder.contractUnion(a68, u.v);
        u.v.dim = ModeBuilder.countPackMembers(a68, u.v.pack);
        // A UNION of one mode is that mode itself.
        if (u.v.dim == 1) {
            return u.v.pack.type;
        } else {
            return ModeBuilder.registerExtraMode(a68, topMoidRef(a68), u.v);
        }
    }

    /** Make SOID data structure (a68_make_soid). */
    public static void makeSoid(A68 a68, Soid s, Attribute sort, Moid type, Attribute attribute) {
        s.attribute = attribute;
        s.sort = sort;
        s.type = type;
        s.cast = false;
    }

    /** Whether mode is not well defined (a68_is_mode_isnt_well). */
    public static boolean isModeIsntWell(A68 a68, Moid p) {
        if (p == null) {
            return true;
        } else if (!ifModeIsWell(a68, p)) {
            return true;
        } else if (p.pack != null) {
            for (Pack q = p.pack; q != null; q = q.next) {
                if (!ifModeIsWell(a68, q.type)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Add SOID data to free chain (a68_free_soid_list). */
    public static void freeSoidList(A68 a68, Soid root) {
        if (root != null) {
            Soid q = root;

            for (; q.next != null; q = q.next) {
                ;
            }
            q.next = a68.topSoidList;
            a68.topSoidList = root;
        }
    }

    /** Add SOID data structure to soid list (a68_add_to_soid_list). */
    public static void addToSoidList(A68 a68, Ref<Soid> root, Node where, Soid soid) {
        if (root.get() != null) {
            Soid r = root.get();
            addToSoidList(a68, Ref.of(() -> r.next, v -> r.next = v), where, soid);
        } else {
            Soid newOne;

            if (a68.topSoidList == null) {
                newOne = new Soid();
            } else {
                newOne = a68.topSoidList;
                a68.topSoidList = a68.topSoidList.next;
            }

            makeSoid(a68, newOne, soid.sort, soid.type, STOP);
            newOne.node = where;
            newOne.next = null;
            root.set(newOne);
        }
    }

    /**
     * Pack soids in moid, gather resulting moids from terminators in a clause
     * (a68_pack_soids_in_moid).
     */
    public static Moid packSoidsInMoid(A68 a68, Soid topSl, Attribute attribute) {
        Moid x = Parser.newMoid(a68);
        Ref<Pack> p;

        x.attribute = attribute;
        x.dim = 0;
        x.sub = null;
        x.equivalentMode = null;
        x.slice = null;
        x.deflexedMode = null;
        x.name = null;
        x.next = null;
        x.pack = null;
        p = Ref.of(() -> x.pack, v -> x.pack = v);
        for (; topSl != null; topSl = topSl.next) {
            // C declares T at function level; a per-iteration local here.
            Pack t = Parser.newPack(a68);
            t.type = topSl.type;
            t.text = null;
            t.node = topSl.node;
            t.next = null;
            x.dim++;
            p.set(t);
            p = Ref.of(() -> t.next, v -> t.next = v);
        }

        return ModeBuilder.registerExtraMode(a68, topMoidRef(a68), x);
    }

    /** Whether P is compatible with Q (a68_is_equal_modes). */
    public static boolean isEqualModes(A68 a68, Moid p, Moid q, int deflex) {
        if (deflex == FORCE_DEFLEXING) {
            return p.deflex() == q.deflex();
        } else if (deflex == ALIAS_DEFLEXING) {
            if (p.attribute == REF_SYMBOL && q.attribute == REF_SYMBOL) {
                return (p == q
                        || MoidsEquivalence.proveMoidEquivalence(a68, p, q)
                        || MoidsEquivalence.proveMoidEquivalence(a68, p.deflex(), q)
                        || p.deflex() == q);
            } else if (!(p.attribute == REF_SYMBOL) && !(q.attribute == REF_SYMBOL)) {
                return (p.deflex() == q.deflex()
                        || MoidsEquivalence.proveMoidEquivalence(a68, p.deflex(), q.deflex()));
            }
            // Note: a mix of REF and non-REF falls through to the final test.
        } else if (deflex == SAFE_DEFLEXING) {
            if (!(p.attribute == REF_SYMBOL) && !(q.attribute == REF_SYMBOL)) {
                return (p.deflex() == q.deflex()
                        || MoidsEquivalence.proveMoidEquivalence(a68, p.deflex(), q.deflex()));
            }
        }

        return (p == q || MoidsEquivalence.proveMoidEquivalence(a68, p, q));
    }

    /**
     * Whether mode is deprefable, i.e. whether it can be either deferred or
     * deprocedured (a68_is_deprefable).
     */
    public static boolean isDeprefable(A68 a68, Moid p) {
        if (p.attribute == REF_SYMBOL) {
            return true;
        } else {
            return (p.attribute == PROC_SYMBOL && p.pack == null);
        }
    }

    /** Deref or deproc the mode P once (a68_depref_once). */
    public static Moid deprefOnce(A68 a68, Moid p) {
        if (p.attribute == REF_SYMBOL && p.sub.attribute == FLEX_SYMBOL) {
            return p.sub.sub;
        } else if (p.attribute == REF_SYMBOL) {
            return p.sub;
        } else if (p.attribute == PROC_SYMBOL && p.pack == null) {
            return p.sub;
        } else {
            return null;
        }
    }

    /** Depref mode completely (a68_depref_completely). */
    public static Moid deprefCompletely(A68 a68, Moid p) {
        while (isDeprefable(a68, p)) {
            p = deprefOnce(a68, p);
        }
        return p;
    }

    /** Deproc_completely (a68_deproc_completely). */
    public static Moid deprocCompletely(A68 a68, Moid p) {
        while (p.attribute == PROC_SYMBOL && p.pack == null) {
            p = deprefOnce(a68, p);
        }
        return p;
    }

    /** Depref rows (a68_depref_rows). */
    public static Moid deprefRows(A68 a68, Moid p, Moid q) {
        if (q == a68.modes.ROWS) {
            while (isDeprefable(a68, p)) {
                p = deprefOnce(a68, p);
            }
            return p;
        } else {
            return q;
        }
    }

    /** Derow mode, strip FLEX and BOUNDS (a68_derow). */
    public static Moid derow(A68 a68, Moid p) {
        if (p.attribute == ROW_SYMBOL || p.attribute == FLEX_SYMBOL) {
            return derow(a68, p.sub);
        } else {
            return p;
        }
    }

    /** Whether rows type (a68_is_rows_type). */
    public static boolean isRowsType(A68 a68, Moid p) {
        switch (p.attribute) {
        case ROW_SYMBOL:
        case FLEX_SYMBOL:
            return true;
        case UNION_SYMBOL: {
            Pack t = p.pack;
            boolean siga = true;
            while (t != null && siga) {
                siga &= isRowsType(a68, t.type);
                t = t.next;
            }
            return siga;
        }
        default:
            return false;
        }
    }

    /** Whether mode is PROC (REF FILE) VOID or FORMAT (a68_is_proc_ref_file_void_or_format). */
    public static boolean isProcRefFileVoidOrFormat(A68 a68, Moid p) {
        if (p == a68.modes.PROC_REF_FILE_VOID) {
            return true;
        } else if (p == a68.modes.FORMAT) {
            return true;
        } else {
            return false;
        }
    }

    /** Whether mode can be transput (a68_is_transput_mode). */
    public static boolean isTransputMode(A68 a68, Moid p, char rw) {
        if (p == a68.modes.INT) {
            return true;
        } else if (p == a68.modes.SHORT_INT) {
            return true;
        } else if (p == a68.modes.SHORT_SHORT_INT) {
            return true;
        } else if (p == a68.modes.LONG_INT) {
            return true;
        } else if (p == a68.modes.LONG_LONG_INT) {
            return true;
        } else if (p == a68.modes.WORD_INT) {
            return true;
        } else if (p == a68.modes.REAL) {
            return true;
        } else if (p == a68.modes.LONG_REAL) {
            return true;
        } else if (p == a68.modes.LONG_LONG_REAL) {
            return true;
        } else if (p == a68.modes.WORD_REAL) {
            return true;
        } else if (p == a68.modes.BOOL) {
            return true;
        } else if (p == a68.modes.CHAR) {
            return true;
        } else if (p == a68.modes.BITS) {
            return true;
        } else if (p == a68.modes.SHORT_BITS) {
            return true;
        } else if (p == a68.modes.SHORT_SHORT_BITS) {
            return true;
        } else if (p == a68.modes.LONG_BITS) {
            return true;
        } else if (p == a68.modes.LONG_LONG_BITS) {
            return true;
        } else if (p == a68.modes.WORD_BITS) {
            return true;
        } else if (p == a68.modes.COMPLEX) {
            return true;
        } else if (p == a68.modes.LONG_COMPLEX) {
            return true;
        } else if (p == a68.modes.LONG_LONG_COMPLEX) {
            return true;
        } else if (p == a68.modes.WORD_COMPLEX) {
            return true;
        } else if (p == a68.modes.ROW_CHAR) {
            return true;
        } else if (p == a68.modes.STRING) {
            return true;
        } else if (p.attribute == UNION_SYMBOL || p.attribute == STRUCT_SYMBOL) {
            for (Pack q = p.pack; q != null; q = q.next) {
                if (!(isTransputMode(a68, q.type, rw)
                        || isProcRefFileVoidOrFormat(a68, q.type))) {
                    return false;
                }
            }
            return true;
        } else if (p.attribute == FLEX_SYMBOL) {
            if (p.sub == a68.modes.ROW_CHAR) {
                return true;
            } else {
                return (rw == 'w' ? isTransputMode(a68, p.sub, rw) : false);
            }
        } else if (p.attribute == ROW_SYMBOL) {
            return (isTransputMode(a68, p.sub, rw)
                    || isProcRefFileVoidOrFormat(a68, p.sub));
        } else {
            return false;
        }
    }

    /** Whether mode is printable (a68_is_printable_mode). */
    public static boolean isPrintableMode(A68 a68, Moid p) {
        if (isProcRefFileVoidOrFormat(a68, p)) {
            return true;
        } else {
            return isTransputMode(a68, p, 'w');
        }
    }

    /** Whether mode is readable (a68_is_readable_mode). */
    public static boolean isReadableMode(A68 a68, Moid p) {
        if (isProcRefFileVoidOrFormat(a68, p)) {
            return true;
        } else if (p.attribute == REF_SYMBOL) {
            return isTransputMode(a68, p.sub, 'r');
        } else if (p.attribute == UNION_SYMBOL) {
            for (Pack q = p.pack; q != null; q = q.next) {
                if (!(q.type.attribute == REF_SYMBOL)) {
                    return false;
                } else if (!isTransputMode(a68, q.type.sub, 'r')) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /** Whether name struct (a68_is_name_struct). */
    public static boolean isNameStruct(A68 a68, Moid p) {
        return (p.name != null ? p.sub.deflex().attribute == STRUCT_SYMBOL : false);
    }

    /** Yield mode to unite to (a68_unites_to). */
    public static Moid unitesTo(A68 a68, Moid m, Moid u) {
        // Uniting U (m).
        Moid v = null;

        if (u == a68.modes.SIMPLIN || u == a68.modes.SIMPLOUT) {
            return m;
        }

        for (Pack p = u.pack; p != null; p = p.next) {
            // Prefer []->[] over []->FLEX [].
            if (m == p.type) {
                v = p.type;
            } else if (v == null && m.deflex() == p.type.deflex()) {
                v = p.type;
            }
        }
        return v;
    }

    /** Whether moid in pack (a68_is_moid_in_pack). */
    public static boolean isMoidInPack(A68 a68, Moid u, Pack v, int deflex) {
        for (; v != null; v = v.next) {
            if (isEqualModes(a68, u, v.type, deflex)) {
                return true;
            }
        }

        return false;
    }

    /** Whether a rows type in pack (a68_is_rows_in_pack). */
    public static boolean isRowsInPack(A68 a68, Pack v) {
        for (; v != null; v = v.next) {
            if (isRowsType(a68, v.type)) {
                return true;
            }
        }

        return false;
    }

    /** Whether P is a subset of Q (a68_is_subset). */
    public static boolean isSubset(A68 a68, Moid p, Moid q, int deflex) {
        boolean j = true;

        for (Pack u = p.pack; u != null && j; u = u.next) {
            j = (j && isMoidInPack(a68, u.type, q.pack, deflex));
        }

        return j;
    }

    /** Whether P can be united to UNION Q (a68_is_unitable). */
    public static boolean isUnitable(A68 a68, Moid p, Moid q, int deflex) {
        if (q.attribute == UNION_SYMBOL) {
            if (p.attribute == UNION_SYMBOL) {
                return isSubset(a68, p, q, deflex);
            } else if (p == a68.modes.ROWS) {
                return isRowsInPack(a68, q.pack);
            } else {
                return isMoidInPack(a68, p, q.pack, deflex);
            }
        }

        return false;
    }

    /**
     * Whether all or some components of U can be firmly coerced to a component
     * mode of V.. (a68_investigate_firm_relations).
     */
    public static void investigateFirmRelations(A68 a68, Pack u, Pack v, boolean[] all, boolean[] some) {
        all[0] = true;
        // Sic: *some starts out true, so the |= below never changes it.
        some[0] = true;
        for (; v != null; v = v.next) {
            boolean k = false;

            for (Pack w = u; w != null; w = w.next) {
                k |= isCoercible(a68, w.type, v.type, FIRM, FORCE_DEFLEXING);
            }
            some[0] |= k;
            all[0] &= k;
        }
    }

    /** Whether there is a soft path from P to Q (a68_is_softly_coercible). */
    public static boolean isSoftlyCoercible(A68 a68, Moid p, Moid q, int deflex) {
        if (isEqualModes(a68, p, q, deflex)) {
            return true;
        } else if (p.attribute == PROC_SYMBOL && p.pack == null) {
            return isSoftlyCoercible(a68, p.sub, q, deflex);
        } else {
            return false;
        }
    }

    /** Whether there is a weak path from P to Q (a68_is_weakly_coercible). */
    public static boolean isWeaklyCoercible(A68 a68, Moid p, Moid q, int deflex) {
        if (isEqualModes(a68, p, q, deflex)) {
            return true;
        } else if (isDeprefable(a68, p)) {
            return isWeaklyCoercible(a68, deprefOnce(a68, p), q, deflex);
        } else {
            return false;
        }
    }

    /** Whether there is a meek path from P to Q (a68_is_meekly_coercible). */
    public static boolean isMeeklyCoercible(A68 a68, Moid p, Moid q, int deflex) {
        if (isEqualModes(a68, p, q, deflex)) {
            return true;
        } else if (isDeprefable(a68, p)) {
            return isMeeklyCoercible(a68, deprefOnce(a68, p), q, deflex);
        } else {
            return false;
        }
    }

    /** Whether there is a firm path from P to Q (a68_is_firmly_coercible). */
    public static boolean isFirmlyCoercible(A68 a68, Moid p, Moid q, int deflex) {
        if (isEqualModes(a68, p, q, deflex)) {
            return true;
        } else if (q == a68.modes.ROWS && isRowsType(a68, p)) {
            return true;
        } else if (isUnitable(a68, p, q, deflex)) {
            return true;
        } else if (isDeprefable(a68, p)) {
            return isFirmlyCoercible(a68, deprefOnce(a68, p), q, deflex);
        } else {
            return false;
        }
    }

    /** Whether firm (a68_is_firm). */
    public static boolean isFirm(A68 a68, Moid p, Moid q) {
        return (isFirmlyCoercible(a68, p, q, SAFE_DEFLEXING)
                || isFirmlyCoercible(a68, q, p, SAFE_DEFLEXING));
    }

    /**
     * Whether P widens to Q (a68_widens_to).
     *
     * <p>This function returns:
     *
     * <p>The destination mode Q if P, or
     * Some other mode which is an intermediate step from P to Q, or
     * NO_MOID if P cannot be widened to Q.
     *
     * <p>This means that if P is known to widen to Q (a68_is_widenable (P,Q) return
     * true) this function can be invoked repeatedly and it will eventually return
     * Q.
     */
    public static Moid widensTo(A68 a68, Moid p, Moid q) {
        Modes m = a68.modes;
        if (p == m.INT) {
            if (q == m.REAL || q == m.COMPLEX) {
                return m.REAL;
            } else {
                return null;
            }
        } else if (p == m.LONG_INT) {
            // Sic: unlike INT and LONG LONG INT, no step towards LONG COMPLEX.
            if (q == m.LONG_REAL) {
                return m.LONG_REAL;
            } else {
                return null;
            }
        } else if (p == m.LONG_LONG_INT) {
            if (q == m.LONG_LONG_REAL || q == m.LONG_LONG_COMPLEX) {
                return m.LONG_LONG_REAL;
            } else {
                return null;
            }
        } else if (p == m.WORD_INT) {
            if (q == m.WORD_REAL) {
                return m.WORD_REAL;
            } else {
                return null;
            }
        } else if (p == m.REAL) {
            if (q == m.COMPLEX) {
                return m.COMPLEX;
            } else {
                return null;
            }
        } else if (p == m.LONG_REAL) {
            if (q == m.LONG_COMPLEX) {
                return m.LONG_COMPLEX;
            } else {
                return null;
            }
        } else if (p == m.LONG_LONG_REAL) {
            if (q == m.LONG_LONG_COMPLEX) {
                return m.LONG_LONG_COMPLEX;
            } else {
                return null;
            }
        } else if (p == m.WORD_REAL) {
            if (q == m.WORD_COMPLEX) {
                return m.WORD_COMPLEX;
            } else {
                return null;
            }
        } else if (p == m.BITS) {
            if (q == m.ROW_BOOL) {
                return m.ROW_BOOL;
            } else if (q == m.FLEX_ROW_BOOL) {
                return m.FLEX_ROW_BOOL;
            } else {
                return null;
            }
        } else if (p == m.SHORT_BITS) {
            if (q == m.ROW_BOOL) {
                return m.ROW_BOOL;
            } else if (q == m.FLEX_ROW_BOOL) {
                return m.FLEX_ROW_BOOL;
            } else {
                return null;
            }
        } else if (p == m.SHORT_SHORT_BITS) {
            if (q == m.ROW_BOOL) {
                return m.ROW_BOOL;
            } else if (q == m.FLEX_ROW_BOOL) {
                return m.FLEX_ROW_BOOL;
            } else {
                return null;
            }
        } else if (p == m.LONG_BITS) {
            if (q == m.ROW_BOOL) {
                return m.ROW_BOOL;
            } else if (q == m.FLEX_ROW_BOOL) {
                return m.FLEX_ROW_BOOL;
            } else {
                return null;
            }
        } else if (p == m.LONG_LONG_BITS) {
            if (q == m.ROW_BOOL) {
                return m.ROW_BOOL;
            } else if (q == m.FLEX_ROW_BOOL) {
                return m.FLEX_ROW_BOOL;
            } else {
                return null;
            }
        } else if (p == m.WORD_BITS) {
            if (q == m.ROW_BOOL) {
                return m.ROW_BOOL;
            } else if (q == m.FLEX_ROW_BOOL) {
                return m.FLEX_ROW_BOOL;
            } else {
                return null;
            }
        } else if (p == m.BYTES && q == m.ROW_CHAR) {
            return m.ROW_CHAR;
        } else if (p == m.LONG_BYTES && q == m.ROW_CHAR) {
            return m.ROW_CHAR;
        } else if (p == m.WORD_BYTES && q == m.ROW_CHAR) {
            return m.ROW_CHAR;
        } else if (p == m.BYTES && q == m.FLEX_ROW_CHAR) {
            return m.FLEX_ROW_CHAR;
        } else if (p == m.LONG_BYTES && q == m.FLEX_ROW_CHAR) {
            return m.FLEX_ROW_CHAR;
        } else if (p == m.WORD_BYTES && q == m.FLEX_ROW_CHAR) {
            return m.FLEX_ROW_CHAR;
        } else {
            return null;
        }
    }

    /** Whether P widens to Q (a68_is_widenable). */
    public static boolean isWidenable(A68 a68, Moid p, Moid q) {
        Moid z = widensTo(a68, p, q);

        if (z != null) {
            return (z == q ? true : isWidenable(a68, z, q));
        } else {
            return false;
        }
    }

    /** Whether P is a REF ROW (a68_is_ref_row). */
    public static boolean isRefRow(A68 a68, Moid p) {
        return (p.name != null ? p.sub.deflex().attribute == ROW_SYMBOL : false);
    }

    /** Whether strong name (a68_is_strong_name). */
    public static boolean isStrongName(A68 a68, Moid p, Moid q) {
        if (p == q) {
            return true;
        } else if (isRefRow(a68, q)) {
            return isStrongName(a68, p, q.name);
        } else {
            return false;
        }
    }

    /** Whether strong slice (a68_is_strong_slice). */
    public static boolean isStrongSlice(A68 a68, Moid p, Moid q) {
        if (p == q || isWidenable(a68, p, q)) {
            return true;
        } else if (q.slice != null) {
            return isStrongSlice(a68, p, q.slice);
        } else if (q.attribute == FLEX_SYMBOL) {
            return isStrongSlice(a68, p, q.sub);
        } else if (isRefRow(a68, q)) {
            return isStrongName(a68, p, q);
        } else {
            return false;
        }
    }

    /** Whether strongly coercible (a68_is_strongly_coercible). */
    public static boolean isStronglyCoercible(A68 a68, Moid p, Moid q, int deflex) {
        // Keep this sequence of statements.
        if (isEqualModes(a68, p, q, deflex)) {
            return true;
        } else if (q == a68.modes.VOID) {
            return true;
        } else if ((q == a68.modes.SIMPLIN || q == a68.modes.ROW_SIMPLIN) && isReadableMode(a68, p)) {
            return true;
        } else if (q == a68.modes.ROWS && isRowsType(a68, p)) {
            return true;
        } else if (isUnitable(a68, p, derow(a68, q), deflex)) {
            return true;
        }

        if (isRefRow(a68, q) && isStrongName(a68, p, q)) {
            return true;
        } else if (q.slice != null && isStrongSlice(a68, p, q)) {
            return true;
        } else if (q.attribute == FLEX_SYMBOL && isStrongSlice(a68, p, q)) {
            return true;
        } else if (isWidenable(a68, p, q)) {
            return true;
        } else if (isDeprefable(a68, p)) {
            return isStronglyCoercible(a68, deprefOnce(a68, p), q, deflex);
        } else if (q == a68.modes.SIMPLOUT || q == a68.modes.ROW_SIMPLOUT) {
            return isPrintableMode(a68, p);
        } else {
            return false;
        }
    }

    /** Basic coercions (a68_basic_coercions). */
    public static boolean basicCoercions(A68 a68, Moid p, Moid q, Attribute c, int deflex) {
        if (isEqualModes(a68, p, q, deflex)) {
            return true;
        } else if (c == NO_SORT) {
            return (p == q);
        } else if (c == SOFT) {
            return isSoftlyCoercible(a68, p, q, deflex);
        } else if (c == WEAK) {
            return isWeaklyCoercible(a68, p, q, deflex);
        } else if (c == MEEK) {
            return isMeeklyCoercible(a68, p, q, deflex);
        } else if (c == FIRM) {
            return isFirmlyCoercible(a68, p, q, deflex);
        } else if (c == STRONG) {
            return isStronglyCoercible(a68, p, q, deflex);
        } else {
            return false;
        }
    }

    /** Whether coercible stowed (a68_is_coercible_stowed). */
    public static boolean isCoercibleStowed(A68 a68, Moid p, Moid q, Attribute c, int deflex) {
        if (c != STRONG) {
            // Such construct is always in a strong position, is it not?
            return false;
        } else if (q == a68.modes.VOID) {
            return true;
        } else if (q.attribute == FLEX_SYMBOL) {
            boolean j = true;

            for (Pack u = p.pack; u != null && j; u = u.next) {
                j &= isCoercible(a68, u.type, q.sub.slice, c, deflex);
            }
            return j;
        } else if (q.attribute == ROW_SYMBOL) {
            boolean j = true;

            for (Pack u = p.pack; u != null && j; u = u.next) {
                j &= isCoercible(a68, u.type, q.slice, c, deflex);
            }
            return j;
        } else if (q.attribute == PROC_SYMBOL || q.attribute == STRUCT_SYMBOL) {
            if (p.dim != q.dim) {
                return false;
            } else {
                Pack u = p.pack, v = q.pack;
                boolean j = true;

                while (u != null && v != null && j) {
                    j &= isCoercible(a68, u.type, v.type, c, deflex);
                    u = u.next;
                    v = v.next;
                }
                return j;
            }
        } else {
            return false;
        }
    }

    /** Whether coercible series (a68_is_coercible_series). */
    public static boolean isCoercibleSeries(A68 a68, Moid p, Moid q, Attribute c, int deflex) {
        if (c == NO_SORT) {
            return false;
        } else if (p == null || q == null) {
            return false;
        } else if (p.attribute == SERIES_MODE && p.pack == null) {
            return false;
        } else if (q.attribute == SERIES_MODE && q.pack == null) {
            return false;
        } else if (p.pack == null) {
            return isCoercible(a68, p, q, c, deflex);
        } else {
            boolean j = true;

            for (Pack u = p.pack; u != null && j; u = u.next) {
                if (u.type != null) {
                    j &= isCoercible(a68, u.type, q, c, deflex);
                }
            }
            return j;
        }
    }

    /**
     * Whether P can be coerced to Q in a C context (a68_is_coercible).
     *
     * <p>If P is a STOWED modes series (A, B, ...) and Q is a routine mode like `proc
     * (X, Y, ...)' then this routine determines whether A can be coerced to X, B
     * to Y, etc.
     */
    public static boolean isCoercible(A68 a68, Moid p, Moid q, Attribute c, int deflex) {
        if (isModeIsntWell(a68, p) || isModeIsntWell(a68, q)) {
            return true;
        } else if (isEqualModes(a68, p, q, deflex)) {
            return true;
        } else if (p == a68.modes.HIP) {
            return true;
        } else if (p.attribute == STOWED_MODE) {
            return isCoercibleStowed(a68, p, q, c, deflex);
        } else if (p.attribute == SERIES_MODE) {
            return isCoercibleSeries(a68, p, q, c, deflex);
        } else if (p == a68.modes.VACUUM && q.deflex().attribute == ROW_SYMBOL) {
            return true;
        } else {
            return basicCoercions(a68, p, q, c, deflex);
        }
    }

    /** Whether coercible in context (a68_is_coercible_in_context). */
    public static boolean isCoercibleInContext(A68 a68, Soid p, Soid q, int deflex) {
        if (p.sort != q.sort) {
            return false;
        } else if (p.type == q.type) {
            return true;
        } else {
            return isCoercible(a68, p.type, q.type, q.sort, deflex);
        }
    }

    /** Whether list Y is balanced (a68_is_balanced). */
    public static boolean isBalanced(A68 a68, Node n, Soid y, Attribute sort) {
        if (sort == STRONG) {
            return true;
        } else {
            boolean k = false;

            for (; y != null && !k; y = y.next) {
                k = (!(y.type.attribute == STOWED_MODE));
            }

            if (k == false) {
                a68.diagnostics.error(n, "construct has no unique mode");
            }
            return k;
        }
    }

    /**
     * A moid from M to which all other members can be coerced.
     * If no fulcrum of the balance is found, return NO_MOID
     * (a68_get_balanced_mode_or_no_mode).
     */
    public static Moid getBalancedModeOrNoMode(A68 a68, Moid m, Attribute sort, boolean returnDepreffed, int deflex) {
        Moid commonMoid = null;

        if (m != null && !isModeIsntWell(a68, m) && m.attribute == UNION_SYMBOL) {
            int deprefLevel;
            boolean siga = true;
            // Test for increasing depreffing.
            for (deprefLevel = 0; siga; deprefLevel++) {
                siga = false;
                // Test the whole pack.
                for (Pack p = m.pack; p != null; p = p.next) {
                    // HIPs are not eligible of course.
                    if (p.type != a68.modes.HIP) {
                        Moid candidate = p.type;
                        int k;
                        // Depref as far as allowed.
                        for (k = deprefLevel; k > 0 && isDeprefable(a68, candidate); k--) {
                            candidate = deprefOnce(a68, candidate);
                        }
                        // Only need testing if all allowed deprefs succeeded.
                        if (k == 0) {
                            Moid to = (returnDepreffed ? deprefCompletely(a68, candidate) : candidate);
                            boolean allCoercible = true;

                            siga = true;
                            for (Pack q = m.pack; q != null && allCoercible; q = q.next) {
                                Moid from = q.type;
                                if (p != q && from != to) {
                                    allCoercible &= isCoercible(a68, from, to, sort, deflex);
                                }
                            }
                            // If the pack is coercible to the candidate, we mark the
                            // candidate.  We continue searching for longest series
                            // of REF REF PROC REF.
                            if (allCoercible) {
                                Moid mark = (returnDepreffed ? p.type : candidate);

                                if (commonMoid == null) {
                                    commonMoid = mark;
                                } else if (candidate.attribute == FLEX_SYMBOL && candidate.deflex() == commonMoid) {
                                    // We prefer FLEX.
                                    commonMoid = mark;
                                }
                            }
                        }
                    }
                }
            }
        }

        return commonMoid;
    }

    /**
     * A moid from M to which all other members can be coerced.
     * If no fulcrum of the balance is found, return M (a68_get_balanced_mode).
     */
    public static Moid getBalancedMode(A68 a68, Moid m, Attribute sort, boolean returnDepreffed, int deflex) {
        Moid commonMoid
            = getBalancedModeOrNoMode(a68, m, sort, returnDepreffed, deflex);
        return commonMoid == null ? m : commonMoid;
    }

    /** Whether we can search a common mode from a clause or not (a68_clause_allows_balancing). */
    public static boolean clauseAllowsBalancing(A68 a68, Attribute att) {
        switch (att) {
        case CLOSED_CLAUSE:
        case CONDITIONAL_CLAUSE:
        case CASE_CLAUSE:
        case SERIAL_CLAUSE:
        case CONFORMITY_CLAUSE:
            return true;
        default:
            break;
        }
        return false;
    }

    /** A unique mode from Z (a68_determine_unique_mode). */
    public static Moid determineUniqueMode(A68 a68, Soid z, int deflex) {
        if (z == null) {
            return null;
        } else {
            Moid x = z.type;

            if (isModeIsntWell(a68, x)) {
                return a68.modes.ERROR;
            }

            // If X is a series containing one union, a68_make_united_mode will
            // return that union (because 'union (union (...))' is the same than
            // 'union (...)') and then a68_get_balanced_mode below will try to
            // balance the modes in that union.  Not what we want.
            if (x.attribute == SERIES_MODE
                    && x.dim == 1
                    && x.pack.type.attribute == UNION_SYMBOL) {
                return x.pack.type;
            }

            x = makeUnitedMode(a68, x);
            if (clauseAllowsBalancing(a68, z.attribute)) {
                return getBalancedMode(a68, x, STRONG, A68_NO_DEPREF, deflex);
            } else {
                return x;
            }
        }
    }

    /** a68_is_c_mode with the C default argument LEVEL = 0. */
    public static boolean isCMode(A68 a68, Moid m) {
        return isCMode(a68, m, 0);
    }

    /**
     * Whether the given mode M is a valid mode for a C formal hole.  See
     * metaproduction rule 561B in ga68.vw (a68_is_c_mode).
     */
    public static boolean isCMode(A68 a68, Moid m, int level) {
        if (m == a68.modes.VOID || m == a68.modes.BOOL || m == a68.modes.CHAR) {
            return true;
        } else if (isIntegral(a68, m)) {
            return true;
        } else if (isBits(a68, m)) {
            return true;
        } else if (isReal(a68, m)) {
            return true;
        } else if (m.attribute == REF_SYMBOL) {
            return isCMode(a68, m.sub, level + 1);
        } else if (m.attribute == PROC_SYMBOL) {
            boolean yieldedModeValid =
                ((level == 0
                  && (m.sub == a68.modes.STRING
                      || (m.sub.attribute == REF_SYMBOL && m.sub.sub == a68.modes.STRING)))
                 || isCMode(a68, m.sub, level + 1));
            boolean paramsValid = true;

            for (Pack z = m.pack; z != null; z = z.next) {
                if (level == 0 && z.type == a68.modes.STRING) {
                    ;
                } else {
                    paramsValid &= isCMode(a68, z.type, level + 1);
                }
            }

            return yieldedModeValid && paramsValid;
        } else if (m.attribute == STRUCT_SYMBOL) {
            boolean fieldsValid = true;

            for (Pack z = m.pack; z != null; z = z.next) {
                fieldsValid &= isCMode(a68, z.type, level + 1);
            }
            return fieldsValid;
        }

        return false;
    }

    /**
     * a68j: whether M is a valid mode for a Java formal hole (-fnest-java):
     * plain modes, STRING, rows of plain modes, names (passed as such, or a
     * Java object, see docs/java-modules.md) and procedures of these.
     */
    public static boolean isJavaMode(A68 a68, Moid m) {
        return isJavaMode(a68, m, 0);
    }

    private static boolean isJavaMode(A68 a68, Moid m, int level) {
        if (m == a68.modes.VOID || m == a68.modes.BOOL || m == a68.modes.CHAR || m == a68.modes.STRING
                || isIntegral(a68, m) || isBits(a68, m) || isReal(a68, m)) {
            return true;
        } else if (m.attribute == REF_SYMBOL) {
            return true;
        } else if (m.attribute == ROW_SYMBOL || m.attribute == FLEX_SYMBOL) {
            Moid e = m.attribute == FLEX_SYMBOL ? m.sub.sub : m.sub;
            return m.dim <= 1 && (e == a68.modes.BOOL || e == a68.modes.CHAR || isIntegral(a68, e)
                    || isBits(a68, e) || isReal(a68, e) || e == a68.modes.STRING);
        } else if (m.attribute == PROC_SYMBOL) {
            // Also as a parameter or result: a functional interface on the
            // Java side (docs/java-modules.md).
            for (Pack z = m.pack; z != null; z = z.next) {
                if (!isJavaMode(a68, z.type, level + 1) || z.type == a68.modes.VOID) {
                    return false;
                }
            }
            return isJavaMode(a68, m.sub, level + 1);
        }
        return false;
    }

    /** Insert coercion A in the tree (a68_make_coercion). */
    public static void makeCoercion(A68 a68, Node l, Attribute a, Moid m) {
        Parser.makeSub(a68, l, l, a);
        l.type = deprefRows(a68, l.type, m);
    }

    /** Make widening coercion (make_widening_coercion). */
    private static void makeWideningCoercion(A68 a68, Node n, Moid p, Moid q) {
        Moid z = widensTo(a68, p, q);

        makeCoercion(a68, n, WIDENING, z);
        if (z != q) {
            makeWideningCoercion(a68, n, z, q);
        }
    }

    /** Make ref rowing coercion (a68_make_ref_rowing_coercion). */
    public static void makeRefRowingCoercion(A68 a68, Node n, Moid p, Moid q) {
        if (p.deflex() != q.deflex()) {
            if (isWidenable(a68, p, q)) {
                makeWideningCoercion(a68, n, p, q);
            } else if (isRefRow(a68, q)) {
                makeRefRowingCoercion(a68, n, p, q.name);
                makeCoercion(a68, n, ROWING, q);
            }
        }
    }

    /** Make rowing coercion (a68_make_rowing_coercion). */
    public static void makeRowingCoercion(A68 a68, Node n, Moid p, Moid q) {
        if (p.deflex() != q.deflex()) {
            if (isWidenable(a68, p, q)) {
                makeWideningCoercion(a68, n, p, q);
            } else if (q.slice != null) {
                makeRowingCoercion(a68, n, p, q.slice);
                makeCoercion(a68, n, ROWING, q);
            } else if (q.attribute == FLEX_SYMBOL) {
                makeRowingCoercion(a68, n, p, q.sub);
            } else if (isRefRow(a68, q)) {
                makeRefRowingCoercion(a68, n, p, q);
            }
        }
    }

    /** Make uniting coercion (a68_make_uniting_coercion). */
    public static void makeUnitingCoercion(A68 a68, Node n, Moid q) {
        makeCoercion(a68, n, UNITING, derow(a68, q));
        if (q.attribute == ROW_SYMBOL || q.attribute == FLEX_SYMBOL) {
            makeRowingCoercion(a68, n, derow(a68, q), q);
        }
    }

    /**
     * Make depreffing coercion to coerce node N from mode P to mode Q in a strong
     * context (a68_make_depreffing_coercion).
     */
    public static void makeDepreffingCoercion(A68 a68, Node n, Moid p, Moid q) {
        if (p.deflex() == q.deflex()) {
            return;
        } else if (q == a68.modes.SIMPLOUT && isPrintableMode(a68, p)) {
            makeCoercion(a68, n, UNITING, q);
        } else if (q == a68.modes.ROW_SIMPLOUT && isPrintableMode(a68, p)) {
            makeCoercion(a68, n, UNITING, a68.modes.SIMPLOUT);
            makeCoercion(a68, n, ROWING, a68.modes.ROW_SIMPLOUT);
        } else if (q == a68.modes.SIMPLIN && isReadableMode(a68, p)) {
            makeCoercion(a68, n, UNITING, q);
        } else if (q == a68.modes.ROW_SIMPLIN && isReadableMode(a68, p)) {
            makeCoercion(a68, n, UNITING, a68.modes.SIMPLIN);
            makeCoercion(a68, n, ROWING, a68.modes.ROW_SIMPLIN);
        } else if (q == a68.modes.ROWS && isRowsType(a68, p)) {
            makeCoercion(a68, n, UNITING, a68.modes.ROWS);
            n.type = a68.modes.ROWS;
        } else if (isWidenable(a68, p, q)) {
            makeWideningCoercion(a68, n, p, q);
        } else if (isUnitable(a68, p, derow(a68, q), SAFE_DEFLEXING)) {
            makeUnitingCoercion(a68, n, q);
        } else if (isRefRow(a68, q) && isStrongName(a68, p, q)) {
            makeRefRowingCoercion(a68, n, p, q);
        } else if (q.slice != null && isStrongSlice(a68, p, q)) {
            makeRowingCoercion(a68, n, p, q);
        } else if (q.attribute == FLEX_SYMBOL && isStrongSlice(a68, p, q)) {
            makeRowingCoercion(a68, n, p, q);
        } else if (p.attribute == REF_SYMBOL) {
            Moid r = deprefOnce(a68, p);
            makeCoercion(a68, n, DEREFERENCING, r);
            makeDepreffingCoercion(a68, n, r, q);
        } else if (p.attribute == PROC_SYMBOL && p.pack == null) {
            Moid r = p.sub;

            makeCoercion(a68, n, DEPROCEDURING, r);
            makeDepreffingCoercion(a68, n, r, q);
        } else if (p != q) {
            MoidsDiagnostics.cannotCoerce(a68, n, p, q, NO_SORT, SKIP_DEFLEXING, STOP);
        }
    }

    /** Whether p is a nonproc mode (that is voided directly) (a68_is_nonproc). */
    public static boolean isNonproc(A68 a68, Moid p) {
        if (p.attribute == PROC_SYMBOL && p.pack == null) {
            return false;
        } else if (p.attribute == REF_SYMBOL) {
            return isNonproc(a68, p.sub);
        } else {
            return true;
        }
    }

    /** Voiden in an appropriate way (a68_make_void). */
    public static void makeVoid(A68 a68, Node p, Moid q) {
        switch (p.attribute) {
        case ASSIGNATION:
        case IDENTITY_RELATION:
        case GENERATOR:
        case CAST:
        case DENOTATION:
            makeCoercion(a68, p, VOIDING, a68.modes.VOID);
            return;
        default:
            break;
        }

        // MORFs are an involved case.
        switch (p.attribute) {
        case SELECTION:
        case SLICE:
        case ROUTINE_TEXT:
        case FORMULA:
        case CALL:
        case IDENTIFIER:
            // A nonproc moid value is eliminated directly.
            if (isNonproc(a68, q)) {
                makeCoercion(a68, p, VOIDING, a68.modes.VOID);
                return;
            } else {
                // Descend the chain of e.g. REF PROC .. until a nonproc moid
                // remains.
                Moid z = q;

                while (!isNonproc(a68, z)) {
                    if (z.attribute == REF_SYMBOL) {
                        makeCoercion(a68, p, DEREFERENCING, z.sub);
                    }
                    // Sic: tests NODE_PACK (p), the pack of the node, not PACK (z).
                    if (z.attribute == PROC_SYMBOL && p.pack == null) {
                        makeCoercion(a68, p, DEPROCEDURING, z.sub);
                    }
                    z = z.sub;
                }
                if (z != a68.modes.VOID) {
                    makeCoercion(a68, p, VOIDING, a68.modes.VOID);
                }
                return;
            }
        default:
            break;
        }

        // All other is voided straight away.
        makeCoercion(a68, p, VOIDING, a68.modes.VOID);
    }

    /** Make strong coercion of node N from mode P to mode Q (a68_make_strong). */
    public static void makeStrong(A68 a68, Node n, Moid p, Moid q) {
        if (q == a68.modes.VOID && p != a68.modes.VOID) {
            makeVoid(a68, n, p);
        } else {
            makeDepreffingCoercion(a68, n, p, q);
        }
    }
}
