// Java port of ga68's a68-postulates.cc: postulates needed for improving
// equivalence of modes.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/** Postulates needed for improving equivalence of modes (a68-postulates.cc). */
public final class Postulates {
    private Postulates() {}

    /** a68_init_postulates: initialise use of postulate-lists. */
    public static void initPostulates(A68 a68) {
        a68.topPostulate = null;
        a68.topPostulateList = null;
    }

    /** a68_free_postulate_list: make old postulates available for new use. */
    public static void freePostulateList(A68 a68, Postulate start, Postulate stop) {
        if (start == stop) {
            return;
        }

        Postulate last = start;
        for (; last.next != stop; last = last.next) {
            ;
        }

        last.next = a68.topPostulateList;
        a68.topPostulateList = start;
    }

    /** a68_make_postulate: add postulates to postulate-list. */
    public static void makePostulate(A68 a68, Ref<Postulate> p, Moid a, Moid b) {
        Postulate newOne;

        if (a68.topPostulateList != null) {
            newOne = a68.topPostulateList;
            a68.topPostulateList = a68.topPostulateList.next;
        } else {
            // C: ggc_cleared_alloc<POSTULATE_T> ().
            newOne = new Postulate();
            a68.newPostulates++;
        }

        newOne.a = a;
        newOne.b = b;
        newOne.next = p.get();
        p.set(newOne);
    }

    /** a68_is_postulated_pair: where postulates are in the list. */
    public static Postulate isPostulatedPair(A68 a68, Postulate p, Moid a, Moid b) {
        for (; p != null; p = p.next) {
            if (p.a == a && p.b == b) {
                return p;
            }
        }

        return null;
    }

    /** a68_is_postulated: where postulate is in the list. */
    public static Postulate isPostulated(A68 a68, Postulate p, Moid a) {
        for (; p != null; p = p.next) {
            if (p.a == a) {
                return p;
            }
        }

        return null;
    }
}
