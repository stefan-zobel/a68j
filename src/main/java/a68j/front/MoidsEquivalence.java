// Java port of ga68's a68-parser-moids-equivalence.cc: prove equivalence of modes.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * Routines for establishing equivalence of modes.
 *
 * <p>After I made this mode equivalencer (in 1993), I found:
 *
 * <p>Algol Bulletin 30.3.3 C.H.A. Koster: On infinite modes, 86-89 [1969],
 *
 * <p>which essentially concurs with this test on mode equivalence I wrote.
 * It is elementary logic anyway: prove equivalence, assuming equivalence.
 */
public final class MoidsEquivalence {
    private MoidsEquivalence() {}

    // Forward declarations of some of the functions defined below: not needed
    // in Java (are_modes_equivalent).

    /**
     * Whether packs are equivalent, same sequence of equivalence modes
     * (are_packs_equivalent), with the default {@code compare_names = true}.
     */
    private static boolean arePacksEquivalent(A68 a68, Pack s, Pack t) {
        return arePacksEquivalent(a68, s, t, true);
    }

    /** Whether packs are equivalent, same sequence of equivalence modes (are_packs_equivalent). */
    private static boolean arePacksEquivalent(A68 a68, Pack s, Pack t,
            boolean compareNames) {
        for (; s != null && t != null; s = s.next, t = t.next) {
            if (!areModesEquivalent(a68, s.type, t.type)) {
                return false;
            }
            if (compareNames) {
                // Note that a name in a pack of a struct may be NULL if the
                // corresponding field is supposed to not be accessible by the user.
                // There is at present one instance of this: the standard prelude
                //
                // TEXT (s) != TEXT (t) is a pointer comparison in C.
                if (s.text != t.text
                        && !(s.text != null
                        && t.text != null
                        && s.text.equals(t.text))) {
                    return false;
                }
            }
        }

        return s == null && t == null;
    }

    /** Whether packs are subsets (is_united_subset). */
    private static boolean isUnitedSubset(A68 a68, Pack s, Pack t) {
        // For all modes in 's' there must be an equivalent in 't'.
        for (Pack p = s; p != null; p = p.next) {
            boolean f = false;
            for (Pack q = t; q != null && !f; q = q.next) {
                f = areModesEquivalent(a68, p.type, q.type);
            }

            if (!f) {
                return false;
            }
        }

        return true;
    }

    /** Whether packs are subsets (are_united_packs_equivalent). */
    private static boolean areUnitedPacksEquivalent(A68 a68, Pack s, Pack t) {
        return isUnitedSubset(a68, s, t) && isUnitedSubset(a68, t, s);
    }

    /** Whether moids A and B are structurally equivalent (are_modes_equivalent). */
    private static boolean areModesEquivalent(A68 a68, Moid a, Moid b) {
        // First lets try some cheap heuristics.

        if (a == null || b == null) {
            // Modes can be NO_MOID in partial argument lists.
            return false;
        } else if (a == a68.modes.ERROR || b == a68.modes.ERROR) {
            return false;
        } else if (a == b) {
            return true;
        } else if (a.attribute != b.attribute) {
            return false;
        } else if (a.dim != b.dim) {
            return false;
        } else if (a.attribute == STANDARD) {
            return (a == b);
        } else if (a.equivalentMode == b || b.equivalentMode == a) {
            return true;
        } else if (Postulates.isPostulatedPair(a68, a68.topPostulate, a, b) != null
                || Postulates.isPostulatedPair(a68, a68.topPostulate, b, a) != null) {
            return true;
        } else if (a.attribute == INDICANT) {
            if (a.node == null || b.node == null) {
                return false;
            } else {
                return a.node == b.node;
            }
        }

        // Investigate structure.

        // We now know that 'a' and 'b' have same attribute, dimension, ...
        if (a.attribute == REF_SYMBOL) {
            // REF MODE
            return areModesEquivalent(a68, a.sub, b.sub);
        } else if (a.attribute == ROW_SYMBOL) {
            // [] MODE
            return areModesEquivalent(a68, a.sub, b.sub);
        } else if (a.attribute == FLEX_SYMBOL) {
            // FLEX [...] MODE
            return areModesEquivalent(a68, a.sub, b.sub);
        } else if (a.attribute == STRUCT_SYMBOL) {
            // STRUCT (...)
            Postulate save = a68.topPostulate;
            Postulates.makePostulate(a68, Ref.of(() -> a68.topPostulate, v -> a68.topPostulate = v), a, b);
            boolean z = arePacksEquivalent(a68, a.pack, b.pack);
            Postulates.freePostulateList(a68, a68.topPostulate, save);
            a68.topPostulate = save;
            return z;
        } else if (a.attribute == UNION_SYMBOL) {
            // UNION (...)
            return areUnitedPacksEquivalent(a68, a.pack, b.pack);
        } else if (a.attribute == PROC_SYMBOL && a.pack == null && b.pack == null) {
            // PROC MOID
            return areModesEquivalent(a68, a.sub, b.sub);
        } else if (a.attribute == PROC_SYMBOL && a.pack != null && b.pack != null) {
            // PROC (...) MOID
            Postulate save = a68.topPostulate;
            Postulates.makePostulate(a68, Ref.of(() -> a68.topPostulate, v -> a68.topPostulate = v), a, b);
            boolean z = areModesEquivalent(a68, a.sub, b.sub);
            if (z) {
                z = arePacksEquivalent(a68, a.pack, b.pack,
                        false /* compare_names */);
            }
            Postulates.freePostulateList(a68, a68.topPostulate, save);
            a68.topPostulate = save;
            return z;
        } else if (a.attribute == SERIES_MODE || a.attribute == STOWED_MODE) {
            // Modes occurring in displays.
            return arePacksEquivalent(a68, a.pack, b.pack);
        }

        return false;
    }

    /** Whether two modes are structurally equivalent (a68_prove_moid_equivalence). */
    public static boolean proveMoidEquivalence(A68 a68, Moid p, Moid q) {
        // Prove two modes to be equivalent under assumption that they indeed are.
        Postulate save = a68.topPostulate;
        boolean z = areModesEquivalent(a68, p, q);
        Postulates.freePostulateList(a68, a68.topPostulate, save);
        a68.topPostulate = save;
        return z;
    }
}
